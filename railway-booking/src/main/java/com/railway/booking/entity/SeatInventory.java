package com.railway.booking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seat_inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_run_id", nullable = false)
    private Long trainRunId;

    @Column(name = "coach_type", nullable = false, length = 20)
    private String coachType;

    @Column(name = "from_station_id", nullable = false)
    private Long fromStationId;

    @Column(name = "to_station_id", nullable = false)
    private Long toStationId;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(name = "rac_seats", nullable = false)
    @Builder.Default
    private Integer racSeats = 0;

    @Column(name = "waitlist_count", nullable = false)
    @Builder.Default
    private Integer waitlistCount = 0;

    @Version
    private Long version;
}
