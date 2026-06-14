package com.daygenie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fetches weather forecast from OpenWeatherMap /forecast endpoint.
 * Returns a simple WeatherInfo record so other services don't depend
 * on the raw API shape.
 */
@Service
@Slf4j
public class WeatherService {

    @Value("${daygenie.weather.api.key}")
    private String apiKey;

    @Value("${daygenie.weather.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record WeatherInfo(
        String summary,
        double temperature,
        double feelsLike,
        double windSpeedKmh,
        int humidity,
        boolean isRainy,
        boolean isStormy,
        boolean isExtreme,
        String icon
    ) {}

    /**
     * Fetch the weather closest to scheduledTime for the given city.
     * Falls back to a neutral result when the API key is not set.
     */
    @SuppressWarnings("unchecked")
    public WeatherInfo getWeather(String city, LocalDateTime scheduledTime) {
        if (apiKey.startsWith("YOUR_")) {
            log.warn("Weather API key not configured – returning stub data");
            return stub();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("cnt", 40)
                .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return stub();

            var list = (java.util.List<Map<String, Object>>) response.get("list");
            Map<String, Object> best = pickClosest(list, scheduledTime);
            if (best == null) return stub();

            Map<String, Object> main = (Map<String, Object>) best.get("main");
            var weatherArr = (java.util.List<Map<String, Object>>) best.get("weather");
            Map<String, Object> wind = (Map<String, Object>) best.get("wind");

            String desc = (String) weatherArr.get(0).get("description");
            String icon = (String) weatherArr.get(0).get("icon");
            double temp = toDouble(main.get("temp"));
            double feels = toDouble(main.get("feels_like"));
            int humidity = ((Number) main.get("humidity")).intValue();
            double windSpeed = toDouble(wind.get("speed")) * 3.6; // m/s → km/h

            boolean rainy  = desc.contains("rain") || desc.contains("drizzle");
            boolean stormy = desc.contains("storm") || desc.contains("thunderstorm");
            boolean extreme= stormy || desc.contains("snow") || windSpeed > 60;

            return new WeatherInfo(capitalize(desc), temp, feels, windSpeed,
                                   humidity, rainy, stormy, extreme, icon);

        } catch (Exception e) {
            log.error("Weather API error: {}", e.getMessage());
            return stub();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> pickClosest(java.util.List<Map<String, Object>> list,
                                            LocalDateTime target) {
        if (list == null || list.isEmpty()) return null;
        Map<String, Object> best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Map<String, Object> entry : list) {
            long dt = ((Number) entry.get("dt")).longValue();
            LocalDateTime entryTime = LocalDateTime.ofEpochSecond(dt, 0,
                java.time.ZoneOffset.UTC);
            long diff = Math.abs(java.time.Duration.between(entryTime, target).toMinutes());
            if (diff < bestDiff) { bestDiff = diff; best = entry; }
        }
        return best;
    }

    private double toDouble(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private WeatherInfo stub() {
        return new WeatherInfo("Weather data unavailable (configure API key)",
                               25.0, 24.0, 10.0, 60,
                               false, false, false, "01d");
    }
}
