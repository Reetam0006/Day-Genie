package com.daygenie.dto;

import com.daygenie.model.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime scheduledTime;
    private String location;
    private String originLocation;
    private TaskCategory category;
    private Priority priority;
    private TaskStatus status;

    private String weatherSummary;
    private Double temperature;
    private String weatherIcon;
    private Double distanceKm;
    private Integer estimatedTravelMinutes;
    private String trafficCondition;

    private RiskLevel riskLevel;
    private String recommendations;
    private Boolean feasible;

    private LocalDateTime createdAt;
}