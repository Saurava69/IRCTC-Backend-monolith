package com.railway.train.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "runs_on_monday")
    private Boolean runsOnMonday;

    @Column(name = "runs_on_tuesday")
    private Boolean runsOnTuesday;

    @Column(name = "runs_on_wednesday")
    private Boolean runsOnWednesday;

    @Column(name = "runs_on_thursday")
    private Boolean runsOnThursday;

    @Column(name = "runs_on_friday")
    private Boolean runsOnFriday;

    @Column(name = "runs_on_saturday")
    private Boolean runsOnSaturday;

    @Column(name = "runs_on_sunday")
    private Boolean runsOnSunday;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
