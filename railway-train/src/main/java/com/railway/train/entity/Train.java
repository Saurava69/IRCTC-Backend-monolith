package com.railway.train.entity;

import com.railway.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Train extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, unique = true, length = 10)
    private String trainNumber;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "train_type", nullable = false, length = 30)
    private TrainType trainType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_station_id", nullable = false)
    private Station sourceStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_station_id", nullable = false)
    private Station destStation;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "train", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Coach> coaches = new ArrayList<>();
}
