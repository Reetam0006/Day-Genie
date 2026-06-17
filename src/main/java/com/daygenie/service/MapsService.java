package com.daygenie.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MapsService {

    @Value("${openrouteservice.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Same RouteInfo record your DecisionEngine already uses — no change needed there
    public record RouteInfo(
            double distanceKm,
            int durationMinutes,
            String trafficCondition  // LIGHT / MODERATE / HEAVY
    ) {}

    // Main method — accepts place names like "Kolkata" or "Park Street, Kolkata"
    public RouteInfo getRoute(String origin, String destination) {
        if (apiKey.startsWith("YOUR_") || origin == null || destination == null) {
            log.warn("ORS API key not configured or missing locations – returning stub");
            return new RouteInfo(5.0, 20, "UNKNOWN");
        }
        try {
            // Step 1: Convert place names to coordinates
            double[] fromCoords = geocode(origin);
            double[] toCoords   = geocode(destination);

            if (fromCoords == null || toCoords == null) {
                log.warn("Could not geocode origin or destination");
                return fallback();
            }

            // Step 2: Get route between coordinates
            String url = "https://api.openrouteservice.org/v2/directions/driving-car"
                    + "?api_key=" + apiKey
                    + "&start=" + fromCoords[1] + "," + fromCoords[0]  // lon,lat
                    + "&end="   + toCoords[1]   + "," + toCoords[0];   // lon,lat

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            JsonNode segment = root
                    .path("features").get(0)
                    .path("properties")
                    .path("segments").get(0);

            double distanceKm   = segment.path("distance").asDouble() / 1000.0;
            int durationMinutes = (int)(segment.path("duration").asDouble() / 60.0);

            // ORS free tier doesn't provide live traffic
            // We estimate based on distance and time like your original code
            String trafficCondition = estimateTraffic(distanceKm, durationMinutes);

            return new RouteInfo(distanceKm, durationMinutes, trafficCondition);

        } catch (Exception e) {
            log.error("ORS API error: {}", e.getMessage());
            return fallback();
        }
    }

    // Convert place name → [lat, lon]
    private double[] geocode(String placeName) {
        try {
            // URL encode properly
            String encoded = java.net.URLEncoder.encode(placeName, "UTF-8");
            String url = "https://api.openrouteservice.org/geocode/search"
                    + "?api_key=" + apiKey
                    + "&text=" + encoded
                    + "&size=1"
                    + "&boundary.country=IN";  // restrict to India

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root   = objectMapper.readTree(response);

            JsonNode features = root.path("features");
            if (features.isEmpty()) {
                log.warn("No geocoding results for: {}", placeName);
                return null;
            }

            JsonNode coords = features.get(0)
                    .path("geometry")
                    .path("coordinates");

            double lon = coords.get(0).asDouble();
            double lat = coords.get(1).asDouble();

            log.info("Geocoded '{}' → lat={}, lon={}", placeName, lat, lon);
            return new double[]{lat, lon};

        } catch (Exception e) {
            log.error("Geocoding failed for '{}': {}", placeName, e.getMessage());
            return null;
        }

    }

    // Estimate traffic since ORS free tier has no live traffic data
    private String estimateTraffic(double distanceKm, int durationMinutes) {
        if (distanceKm == 0) return "UNKNOWN";
        double avgSpeedKmh = distanceKm / (durationMinutes / 60.0);
        if      (avgSpeedKmh > 40) return "LIGHT";
        else if (avgSpeedKmh > 20) return "MODERATE";
        else                       return "HEAVY";
    }

    private RouteInfo fallback() {
        return new RouteInfo(0, 0, "UNKNOWN");
    }
}