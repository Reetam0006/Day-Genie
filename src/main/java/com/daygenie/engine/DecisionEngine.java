package com.daygenie.engine;

import com.daygenie.model.*;
import com.daygenie.service.MapsService.RouteInfo;
import com.daygenie.service.WeatherService.WeatherInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based Decision & Risk Analysis Engine.
 *
 * Evaluates a task against weather + route data and returns:
 *  - RiskLevel (LOW / MEDIUM / HIGH)
 *  - feasibility flag
 *  - list of actionable recommendations
 */
@Component
public class DecisionEngine {

    public record DecisionResult(
        RiskLevel riskLevel,
        boolean feasible,
        List<String> recommendations
    ) {}

    public DecisionResult evaluate(Task task, WeatherInfo weather, RouteInfo route) {
        List<String> recs = new ArrayList<>();
        int riskScore = 0;

        // ── Weather rules ──────────────────────────────────────────────────────
        if (weather.isExtreme()) {
            riskScore += 3;
            recs.add("⛈️ Extreme weather expected. Strongly consider rescheduling.");
        } else if (weather.isStormy()) {
            riskScore += 2;
            recs.add("🌩️ Storm alert. Avoid travel if possible; take rain gear.");
        } else if (weather.isRainy()) {
            riskScore += 1;
            recs.add("🌧️ Rain expected. Carry an umbrella and allow extra travel time.");
        }

        if (weather.temperature() > 40) {
            riskScore += 1;
            recs.add("🌡️ High temperature (" + weather.temperature() + "°C). Stay hydrated.");
        } else if (weather.temperature() < 5) {
            riskScore += 1;
            recs.add("🥶 Cold weather expected. Dress warmly.");
        }

        if (weather.windSpeedKmh() > 50) {
            riskScore += 1;
            recs.add("💨 Strong winds (" + (int) weather.windSpeedKmh() + " km/h). Be cautious outdoors.");
        }

        // ── Traffic rules ──────────────────────────────────────────────────────
        if ("HEAVY".equals(route.trafficCondition())) {
            riskScore += 2;
            recs.add("🚦 Heavy traffic on your route. Leave at least " +
                     (route.durationMinutes() + 20) + " minutes early.");
        } else if ("MODERATE".equals(route.trafficCondition())) {
            riskScore += 1;
            recs.add("🚗 Moderate traffic. Leave " +
                     (route.durationMinutes() + 10) + " minutes early to be safe.");
        }

        // ── Distance rules ─────────────────────────────────────────────────────
        if (route.distanceKm() > 30) {
            recs.add("📍 Long journey (" + String.format("%.1f", route.distanceKm()) +
                     " km). Plan for fuel/ticket and comfort breaks.");
        }

        // ── Category-specific rules ────────────────────────────────────────────
        if (task.getCategory() == TaskCategory.TRAVEL && weather.isRainy()) {
            recs.add("🚌 Consider public transport to avoid parking issues in rain.");
        }
        if (task.getCategory() == TaskCategory.HEALTH && riskScore >= 2) {
            recs.add("🏥 Medical appointments are important. If rescheduling, call ahead.");
        }

        // ── Priority override ──────────────────────────────────────────────────
        if (task.getPriority() == Priority.HIGH && riskScore >= 2) {
            recs.add("⚠️ This is a HIGH priority task. Plan contingencies carefully.");
        }

        // ── Score → level ──────────────────────────────────────────────────────
        RiskLevel level;
        if      (riskScore >= 4) level = RiskLevel.HIGH;
        else if (riskScore >= 2) level = RiskLevel.MEDIUM;
        else                     level = RiskLevel.LOW;

        // Feasible = not extreme weather AND route is known
        boolean feasible = !weather.isExtreme() && !"UNKNOWN".equals(route.trafficCondition());

        if (recs.isEmpty()) {
            recs.add("✅ Conditions look good. You're all set!");
        }

        return new DecisionResult(level, feasible, recs);
    }
}
