package com.railway.train.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "route_stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "halt_minutes")
    @Builder.Default
    private Integer haltMinutes = 0;

    @Column(name = "distance_from_origin_km")
    @Builder.Default
    private Integer distanceFromOriginKm = 0;

    @Column(name = "day_offset")
    @Builder.Default
    private Integer dayOffset = 0;
}
