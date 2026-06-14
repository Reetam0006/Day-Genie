package com.daygenie.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private LocalDateTime scheduledTime;

    private String location;

    private String originLocation;

    @Enumerated(EnumType.STRING)
    private TaskCategory category;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    // ── Context data ──────────────────────────────────────────────────
    private String weatherSummary;
    private Double temperature;
    private String weatherIcon;

    private Double distanceKm;
    private Integer estimatedTravelMinutes;
    private String trafficCondition;

    // ── Decision engine output ────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.UNKNOWN;

    @Column(length = 1000)
    private String recommendations;

    private Boolean feasible;

    @Builder.Default
    private Boolean parsedFromNlp = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"tasks", "password"})
    private User user;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}