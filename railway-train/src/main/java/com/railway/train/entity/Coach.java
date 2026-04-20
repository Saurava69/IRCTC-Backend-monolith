package com.railway.train.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coaches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(name = "coach_number", nullable = false, length = 10)
    private String coachNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "coach_type", nullable = false, length = 20)
    private CoachType coachType;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "total_berths", nullable = false)
    private Integer totalBerths;

    @Column(name = "sequence_in_train", nullable = false)
    private Integer sequenceInTrain;
}
