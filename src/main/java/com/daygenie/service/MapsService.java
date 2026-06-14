package com.daygenie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Fetches distance and estimated travel time from Google Distance Matrix API.
 */
@Service
@Slf4j
public class MapsService {

    @Value("${daygenie.maps.api.key}")
    private String apiKey;

    @Value("${daygenie.maps.distance.url}")
    private String distanceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record RouteInfo(
        double distanceKm,
        int durationMinutes,
        String trafficCondition   // LIGHT / MODERATE / HEAVY
    ) {}

    @SuppressWarnings("unchecked")
    public RouteInfo getRoute(String origin, String destination) {
        if (apiKey.startsWith("YOUR_") || origin == null || destination == null) {
            log.warn("Maps API key not configured or missing locations – returning stub");
            return new RouteInfo(5.0, 20, "UNKNOWN");
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(distanceUrl)
                .queryParam("origins", origin)
                .queryParam("destinations", destination)
                .queryParam("departure_time", "now")
                .queryParam("traffic_model", "best_guess")
                .queryParam("key", apiKey)
                .toUriString();

            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null) return fallback();

            var rows = (java.util.List<Map<String, Object>>) resp.get("rows");
            if (rows == null || rows.isEmpty()) return fallback();

            var elements = (java.util.List<Map<String, Object>>) rows.get(0).get("elements");
            if (elements == null || elements.isEmpty()) return fallback();

            Map<String, Object> el = elements.get(0);
            if (!"OK".equals(el.get("status"))) return fallback();

            Map<String, Object> dist  = (Map<String, Object>) el.get("distance");
            Map<String, Object> dur   = (Map<String, Object>) el.get("duration");
            Map<String, Object> durTr = (Map<String, Object>) el.get("duration_in_traffic");

            double km = ((Number) dist.get("value")).doubleValue() / 1000.0;
            int normalMin  = ((Number) dur.get("value")).intValue()   / 60;
            int trafficMin = durTr != null
                           ? ((Number) durTr.get("value")).intValue() / 60
                           : normalMin;

            String condition;
            double ratio = (double) trafficMin / Math.max(normalMin, 1);
            if      (ratio < 1.2) condition = "LIGHT";
            else if (ratio < 1.6) condition = "MODERATE";
            else                  condition = "HEAVY";

            return new RouteInfo(km, trafficMin, condition);

        } catch (Exception e) {
            log.error("Maps API error: {}", e.getMessage());
            return fallback();
        }
    }

    private RouteInfo fallback() {
        return new RouteInfo(0, 0, "UNKNOWN");
    }
}
