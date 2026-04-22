package com.railway.booking.repository;

import com.railway.booking.entity.TrainRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainRunRepository extends JpaRepository<TrainRun, Long> {

    Optional<TrainRun> findByTrainIdAndRunDate(Long trainId, LocalDate runDate);

    List<TrainRun> findByTrainIdAndRunDateBetween(Long trainId, LocalDate from, LocalDate to);

    List<TrainRun> findByRunDate(LocalDate runDate);

    boolean existsByScheduleIdAndRunDate(Long scheduleId, LocalDate runDate);

    @Query("SELECT tr.id FROM TrainRun tr WHERE tr.status = 'SCHEDULED'")
    List<Long> findAllActiveTrainRunIds();
}
