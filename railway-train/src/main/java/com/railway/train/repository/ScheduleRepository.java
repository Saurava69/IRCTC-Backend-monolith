package com.railway.train.repository;

import com.railway.train.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByTrainIdAndIsActiveTrue(Long trainId);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.train JOIN FETCH s.route WHERE s.isActive = true")
    List<Schedule> findAllActiveWithTrainAndRoute();
}
