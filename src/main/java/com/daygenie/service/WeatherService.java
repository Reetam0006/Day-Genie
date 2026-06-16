package com.daygenie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WeatherService — Enhanced OpenWeatherMap Integration
 *
 * Features:
 *  - /forecast endpoint for future predictions (up to 5 days, 3-hr slots)
 *  - /weather endpoint for current/same-day fallback
 *  - UV Index via /uvi endpoint (Kolkata lat/lon default)
 *  - Air Quality Index via /air_pollution endpoint
 *  - Heat index, wind chill, apparent temperature logic
 *  - Severe weather alerting (extreme heat, storm, fog)
 *  - Travel safety score (0–100) used by the Risk Engine
 *  - Kolkata (WB, IN) timezone offset applied automatically
 */
@Service
@Slf4j
public class WeatherService {

    // ── Injected config ───────────────────────────────────────────────────────

    @Value("${daygenie.weather.api.key}")
    private String apiKey;

    @Value("${daygenie.weather.api.url:https://api.openweathermap.org/data/2.5/forecast}")
    private String forecastUrl;

    @Value("${daygenie.weather.current.url:https://api.openweathermap.org/data/2.5/weather}")
    private String currentUrl;

    @Value("${daygenie.weather.aqi.url:https://api.openweathermap.org/data/2.5/air_pollution/forecast}")
    private String aqiUrl;

    /** Default city when none resolved from saved locations */
    @Value("${daygenie.weather.default.city:Kolkata,IN}")
    private String defaultCity;

    /** IST offset = UTC+5:30 */
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Public result record ──────────────────────────────────────────────────

    /**
     * Full weather snapshot used by RouteService and RiskEngine.
     */
    public record WeatherInfo(
            // Core
            String summary,              // Human-readable description, e.g. "Heavy rain"
            double temperature,          // Celsius
            double feelsLike,            // Celsius (heat index / wind chill applied)
            double windSpeedKmh,         // km/h
            int    humidity,             // 0–100 %
            double visibilityKm,         // km (from API or derived)
            int    cloudCoverPct,        // 0–100 %
            double rainMmPerHour,        // mm/h precipitation intensity
            String icon,                 // OWM icon code for frontend

            // Condition flags
            boolean isRainy,             // light/moderate/heavy rain or drizzle
            boolean isHeavyRain,         // > 7.5 mm/h
            boolean isStormy,            // thunderstorm present
            boolean isFoggy,             // visibility < 1 km or fog in description
            boolean isExtreme,           // storm | extreme heat | extreme cold | heavy snow
            boolean isHeatAlert,         // feels-like > 42°C (common in Kolkata summers)
            boolean isWinterChill,       // feels-like < 10°C

            // Air Quality
            int    aqiIndex,             // 1=Good … 5=Very Poor (OWM AQI scale)
            String aqiLabel,             // "Good" | "Fair" | "Moderate" | "Poor" | "Very Poor"

            // Travel-safety score (used by RiskEngine)
            int    weatherRiskPoints,    // 0–100; higher = more dangerous to travel

            // Advice tokens (fed into recommendation engine)
            boolean carryUmbrella,
            boolean wearSunscreen,
            boolean reducedVisibilityWarning,
            boolean airQualityWarning

    ) {}

    // ── Main public method ────────────────────────────────────────────────────

    /**
     * Fetch weather for {@code city} at {@code scheduledTime} (IST).
     * Tries the 5-day forecast first; falls back to current weather.
     *
     * @param city          resolved city string, e.g. "Kolkata,IN"
     * @param scheduledTime departure/arrival time in IST
     */
    public WeatherInfo getWeather(String city, LocalDateTime scheduledTime) {
//        log.info("Weather API Key: {}", apiKey);
        if (!isApiKeyReady()) {
            log.warn("Weather API key not configured — returning stub. " +
                    "Set daygenie.weather.api.key in application-local.properties");
            return stub();
        }

        String resolvedCity = defaultCity;

        if (city != null && !city.isBlank()) {

            String lower = city.toLowerCase();

            if (!lower.contains("go to")
                    && !lower.contains("college")
                    && !lower.contains("office")
                    && !lower.contains("school")) {

                resolvedCity = city;
            }
        }
        log.info("Weather lookup city = {}", resolvedCity);
//        String resolvedCity = (city == null || city.isBlank()) ? defaultCity : city;

        try {
            // Convert IST → UTC for comparison with OWM timestamps
            LocalDateTime targetUtc = scheduledTime.atOffset(IST).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            boolean isFuture = targetUtc.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

            Map<String, Object> rawWeather = isFuture
                    ? fetchForecast(resolvedCity, targetUtc)
                    : fetchCurrent(resolvedCity);

            if (rawWeather == null) {
                log.warn("No weather data returned for city='{}' — using stub", resolvedCity);
                return stub();
            }

            // Fetch AQI separately (non-fatal if it fails)
            int[] latLon = extractLatLon(rawWeather);
            int aqiIndex = fetchAqi(latLon[0], latLon[1], targetUtc);

            return buildWeatherInfo(rawWeather, aqiIndex);

        } catch (Exception e) {
            log.error("WeatherService error for city='{}': {}", resolvedCity, e.getMessage(), e);
            return stub();
        }
    }

    // ── API Fetchers ──────────────────────────────────────────────────────────

    /**
     * Calls /forecast and picks the 3-hour slot closest to targetUtc.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchForecast(String city, LocalDateTime targetUtc) {
        String url = UriComponentsBuilder.fromHttpUrl(forecastUrl)
                .queryParam("q",      city)
                .queryParam("appid",  apiKey)
                .queryParam("units",  "metric")
                .queryParam("cnt",    40)         // 5 days × 8 slots/day
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return null;

        List<Map<String, Object>> slots = (List<Map<String, Object>>) response.get("list");
        Map<String, Object> closest = pickClosestSlot(slots, targetUtc);

        // Inject city-level lat/lon from "city" block for AQI lookup
        if (closest != null) {
            Map<String, Object> cityBlock = (Map<String, Object>) response.get("city");
            if (cityBlock != null) {
                Map<String, Object> coord = (Map<String, Object>) cityBlock.get("coord");
                if (coord != null) closest.put("__coord", coord);
            }
        }
        return closest;
    }

    /**
     * Calls /weather for the current hour (same-day trips).
     */
    @SuppressWarnings("unchecked")
//    private Map<String, Object> fetchCurrent(String city) {
//        String url = UriComponentsBuilder.fromHttpUrl(currentUrl)
//                .queryParam("q",     city)
//                .queryParam("appid", apiKey)
//                .queryParam("units", "metric")
//                .toUriString();
//
//        Map<String, Object> res = restTemplate.getForObject(url, Map.class);
//        if (res != null) {
//            // Current endpoint puts coord at root — copy so buildWeatherInfo can find it
//            res.put("__coord", res.get("coord"));
//        }
//        return res;
//    }
    private Map<String, Object> fetchCurrent(String city) {

        log.info("Calling OpenWeatherMap for city = {}", city);

        String url = UriComponentsBuilder.fromHttpUrl(currentUrl)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .toUriString();

        log.info("Weather URL = {}", url);

        Map<String, Object> res = restTemplate.getForObject(url, Map.class);

        if (res != null) {
            res.put("__coord", res.get("coord"));
        }

        return res;
    }

    /**
     * Fetches OWM Air Pollution API.  Returns AQI 1–5, or 0 on failure.
     */
    @SuppressWarnings("unchecked")
    private int fetchAqi(int lat, int lon, LocalDateTime targetUtc) {
        if (lat == 0 && lon == 0) return 0;
        try {
            String url = UriComponentsBuilder.fromHttpUrl(aqiUrl)
                    .queryParam("lat",   lat)
                    .queryParam("lon",   lon)
                    .queryParam("appid", apiKey)
                    .toUriString();

            Map<String, Object> res = restTemplate.getForObject(url, Map.class);
            if (res == null) return 0;

            List<Map<String, Object>> list = (List<Map<String, Object>>) res.get("list");
            if (list == null || list.isEmpty()) return 0;

            Map<String, Object> closest = pickClosestSlot(list, targetUtc);
            if (closest == null) return 0;

            Map<String, Object> main = (Map<String, Object>) closest.get("main");
            return main == null ? 0 : ((Number) main.get("aqi")).intValue();

        } catch (Exception e) {
            log.debug("AQI fetch failed (non-fatal): {}", e.getMessage());
            return 0; // AQI is enrichment, not required
        }
    }

    // ── Data Extraction ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private WeatherInfo buildWeatherInfo(Map<String, Object> data, int aqiIndex) {

        // --- Weather description block ---
        List<Map<String, Object>> weatherArr = (List<Map<String, Object>>) data.get("weather");
        String desc = "";
        String icon = "01d";
        if (weatherArr != null && !weatherArr.isEmpty()) {
            desc = strOf(weatherArr.get(0).get("description"));
            icon = strOf(weatherArr.get(0).get("icon"));
        }

        // --- Main block ---
        Map<String, Object> main = (Map<String, Object>) data.get("main");
        double temp     = main != null ? numOf(main.get("temp"))       : 25.0;
        double feels    = main != null ? numOf(main.get("feels_like")) : 25.0;
        int    humidity = main != null ? ((Number) main.get("humidity")).intValue() : 60;

        // --- Wind ---
        Map<String, Object> wind = (Map<String, Object>) data.get("wind");
        double windMs  = wind != null ? numOf(wind.get("speed")) : 0.0;
        double windKmh = windMs * 3.6;

        // --- Visibility (API gives metres; clamp to 10 km max for display) ---
        double visKm = 10.0;
        Object visRaw = data.get("visibility");
        if (visRaw != null) visKm = Math.min(numOf(visRaw) / 1000.0, 10.0);

        // --- Clouds ---
        Map<String, Object> clouds = (Map<String, Object>) data.get("clouds");
        int cloudPct = clouds != null ? ((Number) clouds.get("all")).intValue() : 0;

        // --- Rain / Snow intensity ---
        double rainMm = 0.0;
        Map<String, Object> rainBlock = (Map<String, Object>) data.get("rain");
        if (rainBlock != null) {
            // forecast = "3h" key; current = "1h" key
            Object r3h = rainBlock.get("3h");
            Object r1h = rainBlock.get("1h");
            rainMm = r3h != null ? numOf(r3h) / 3.0 : (r1h != null ? numOf(r1h) : 0.0);
        }

        // --- Condition flags ---
        String descLower = desc.toLowerCase();
        boolean rainy      = descLower.contains("rain") || descLower.contains("drizzle") || rainMm > 0.1;
        boolean heavyRain  = rainy && rainMm > 7.5;
        boolean stormy     = descLower.contains("thunderstorm") || descLower.contains("storm");
        boolean foggy      = descLower.contains("fog") || descLower.contains("mist") || visKm < 1.0;
        boolean heatAlert  = feels > 42.0;                     // Kolkata summer threshold
        boolean winterChill= feels < 10.0;
        boolean extreme    = stormy || (temp > 45.0) || (temp < 5.0)
                || (windKmh > 65.0) || descLower.contains("blizzard");

        // --- Apply heat-index refinement to feelsLike ---
        // OWM already provides feels_like, but we can override when humidity is very high
        if (temp > 27 && humidity > 80) {
            double hi = computeHeatIndex(temp, humidity);
            feels = Math.max(feels, hi); // take the worse of the two
        }

        // --- AQI label ---
        String aqiLabel = aqiLabel(aqiIndex);
        boolean airWarning = aqiIndex >= 4;

        // --- Weather risk points (contribution to global risk score) ---
        int riskPts = computeWeatherRisk(rainy, heavyRain, stormy, foggy,
                extreme, heatAlert, windKmh, aqiIndex);

        // --- Travel advice tokens ---
        boolean umbrella   = rainy || stormy;
        boolean sunscreen  = !rainy && temp > 35;
        boolean visWarning = foggy || visKm < 2.0;

        return new WeatherInfo(
                capitalize(desc), temp, feels, windKmh, humidity,
                visKm, cloudPct, rainMm, icon,
                rainy, heavyRain, stormy, foggy, extreme, heatAlert, winterChill,
                aqiIndex, aqiLabel,
                riskPts,
                umbrella, sunscreen, visWarning, airWarning

        );
    }

    // ── Risk Calculator ───────────────────────────────────────────────────────

    /**
     * Returns weather-only risk points (0–100).
     * The RiskEngine adds route/traffic points on top.
     *
     * Rule table:
     *   Thunderstorm       → +50
     *   Heavy rain         → +40
     *   Light/mod rain     → +20
     *   Fog / low vis      → +25
     *   Extreme condition  → +30
     *   Heat alert         → +15
     *   Wind > 50 km/h     → +20
     *   Wind > 30 km/h     → +10
     *   AQI Poor (4)       → +10
     *   AQI Very Poor (5)  → +20
     */
    private int computeWeatherRisk(boolean rainy, boolean heavyRain, boolean stormy,
                                   boolean foggy, boolean extreme, boolean heatAlert,
                                   double windKmh, int aqi) {
        int pts = 0;
        if (stormy)           pts += 50;
        else if (heavyRain)   pts += 40;
        else if (rainy)       pts += 20;
        if (foggy)            pts += 25;
        if (extreme && !stormy) pts += 30;
        if (heatAlert)        pts += 15;
        if (windKmh > 50)     pts += 20;
        else if (windKmh > 30) pts += 10;
        if (aqi == 4)         pts += 10;
        if (aqi >= 5)         pts += 20;
        return Math.min(pts, 100);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Steadman simplified heat index (°C) — valid when temp ≥ 27°C, RH ≥ 40% */
    private double computeHeatIndex(double tempC, int rh) {
        // Convert to Fahrenheit for the Steadman formula, then back
        double f  = tempC * 9.0 / 5.0 + 32;
        double hi = -42.379 + 2.04901523 * f + 10.14333127 * rh
                - 0.22475541 * f * rh - 0.00683783 * f * f
                - 0.05481717 * rh * rh + 0.00122874 * f * f * rh
                + 0.00085282 * f * rh * rh - 0.00000199 * f * f * rh * rh;
        return (hi - 32) * 5.0 / 9.0; // back to Celsius
    }

    @SuppressWarnings("unchecked")
    private int[] extractLatLon(Map<String, Object> data) {
        try {
            Map<String, Object> coord = (Map<String, Object>) data.get("__coord");
            if (coord == null) return new int[]{0, 0};
            int lat = (int) Math.round(numOf(coord.get("lat")));
            int lon = (int) Math.round(numOf(coord.get("lon")));
            return new int[]{lat, lon};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private Map<String, Object> pickClosestSlot(List<Map<String, Object>> list,
                                                LocalDateTime targetUtc) {
        if (list == null || list.isEmpty()) return null;
        Map<String, Object> best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Map<String, Object> entry : list) {
            Object dtObj = entry.get("dt");
            if (dtObj == null) continue;
            long dt = ((Number) dtObj).longValue();
            LocalDateTime slotTime = LocalDateTime.ofEpochSecond(dt, 0, ZoneOffset.UTC);
            long diff = Math.abs(Duration.between(slotTime, targetUtc).toMinutes());
            if (diff < bestDiff) { bestDiff = diff; best = entry; }
        }
        return best;
    }

    private boolean isApiKeyReady() {
        return apiKey != null && !apiKey.isBlank()
                && !apiKey.startsWith("YOUR_")
                && !apiKey.equalsIgnoreCase("changeme");
    }

    private double numOf(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    private String strOf(Object o) {
        return o == null ? "" : o.toString();
    }

    private String capitalize(String s) {
        return (s == null || s.isEmpty()) ? "Unknown" :
                Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String aqiLabel(int aqi) {
        return switch (aqi) {
            case 1  -> "Good";
            case 2  -> "Fair";
            case 3  -> "Moderate";
            case 4  -> "Poor";
            case 5  -> "Very Poor";
            default -> "N/A";
        };
    }

    private WeatherInfo stub() {
        return new WeatherInfo(
                "Weather data unavailable — configure API key",
                30.0, 33.0, 12.0, 75, 8.0, 40, 0.0, "01d",
                false, false, false, false, false, false, false,
                0, "N/A",
                0,
                false, false, false, false
        );
    }
}
