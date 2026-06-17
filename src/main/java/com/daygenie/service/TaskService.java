package com.daygenie.service;

import com.daygenie.engine.DecisionEngine;
import com.daygenie.model.*;
import com.daygenie.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;
    private final WeatherService weatherService;
    private final MapsService mapsService;
    private final DecisionEngine decisionEngine;
    private final NlpParserService nlpParser;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional
    public Task createTask(Task task, Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        task.setUser(user);
        if (task.getOriginLocation() == null && user.getDefaultLocation() != null) {
            task.setOriginLocation(user.getDefaultLocation());
        }
        Task saved = taskRepo.save(task);
        enrichWithContext(saved);
        return taskRepo.save(saved);
    }

    @Transactional
    public Task createTaskFromNlp(String rawInput, Long userId) {
        NlpParserService.ParsedTask parsed = nlpParser.parse(rawInput);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Task task = Task.builder()
                .title(parsed.title)
                .description(parsed.description)
                .scheduledTime(parsed.scheduledTime)
                .location(parsed.location)
                .originLocation(parsed.originLocation != null ? parsed.originLocation : user.getDefaultLocation())
                .category(parsed.category)
                .priority(Priority.MEDIUM)
                .status(TaskStatus.PENDING)
                .parsedFromNlp(true)
                .user(user)
                .build();

        Task saved = taskRepo.save(task);
        enrichWithContext(saved);
        return taskRepo.save(saved);
    }

    public Task getTask(Long taskId, Long userId) {
        return taskRepo.findById(taskId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }

    /** Returns only active (non-completed, non-cancelled) tasks */
    public List<Task> getActiveTasks(Long userId) {
        return taskRepo.findActiveTasksByUserId(userId);
    }

    public List<Task> getTasksForToday(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        return taskRepo.findActiveTasksForDateRange(userId, start, end);
    }

    @Transactional
    public Task updateTask(Long taskId, Task updates, Long userId) {
        Task task = getTask(taskId, userId);
        if (updates.getTitle()         != null) task.setTitle(updates.getTitle());
        if (updates.getDescription()   != null) task.setDescription(updates.getDescription());
        if (updates.getScheduledTime() != null) task.setScheduledTime(updates.getScheduledTime());
        if (updates.getLocation()      != null) task.setLocation(updates.getLocation());
        if (updates.getCategory()      != null) task.setCategory(updates.getCategory());
        if (updates.getPriority()      != null) task.setPriority(updates.getPriority());
        if (updates.getStatus()        != null) task.setStatus(updates.getStatus());
        return taskRepo.save(task);
    }

    /** Dedicated complete — no context re-enrichment needed */
    @Transactional
    public void completeTask(Long taskId, Long userId) {
        Task task = getTask(taskId, userId);
        task.setStatus(TaskStatus.COMPLETED);
        taskRepo.save(task);
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = getTask(taskId, userId);
        taskRepo.delete(task);
    }

    // ── Context enrichment ────────────────────────────────────────────────────

    public void enrichWithContext(Task task) {
        String city = task.getLocation() != null ? task.getLocation()
                : (task.getUser() != null ? task.getUser().getDefaultLocation() : null);

        WeatherService.WeatherInfo weather = (city != null)
                ? weatherService.getWeather(city, task.getScheduledTime())
                : new WeatherService.WeatherInfo(
                "N/A",       // summary
                0.0,         // temperature
                0.0,         // feelsLike
                0.0,         // windSpeedKmh
                0,           // humidity
                0.0,         // visibilityKm
                0,           // cloudCoverPct
                0.0,         // rainMmPerHour
                "01d",       // icon
                false,       // isRainy
                false,       // isHeavyRain
                false,       // isStormy
                false,       // isFoggy
                false,       // isExtreme
                false,       // isHeatAlert
                false,       // isWinterChill
                0,           // aqiIndex
                "N/A",       // aqiLabel
                0,           // weatherRiskPoints
                false,       // carryUmbrella
                false,       // wearSunscreen
                false,       // reducedVisibilityWarning
                false        // airQualityWarning
        );

        MapsService.RouteInfo route = (task.getOriginLocation() != null && task.getLocation() != null)
                ? mapsService.getRoute(task.getOriginLocation(), task.getLocation())
                : new MapsService.RouteInfo(0, 0, "UNKNOWN");

        task.setWeatherSummary(weather.summary());
        task.setTemperature(weather.temperature());
        task.setWeatherIcon(weather.icon());

        task.setDistanceKm(route.distanceKm());
        task.setEstimatedTravelMinutes(route.durationMinutes());
        task.setTrafficCondition(route.trafficCondition());

        DecisionEngine.DecisionResult decision = decisionEngine.evaluate(task, weather, route);
        task.setRiskLevel(decision.riskLevel());
        task.setFeasible(decision.feasible());
        task.setRecommendations(String.join("\n", decision.recommendations()));
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    @Transactional
    public void refreshUpcomingTaskContexts() {
        List<Task> upcoming = taskRepo.findUpcomingPendingTasks(
                LocalDateTime.now(), LocalDateTime.now().plusHours(24));
        log.info("Refreshing context for {} upcoming tasks", upcoming.size());
        for (Task t : upcoming) {
            enrichWithContext(t);
            taskRepo.save(t);
        }
    }
}