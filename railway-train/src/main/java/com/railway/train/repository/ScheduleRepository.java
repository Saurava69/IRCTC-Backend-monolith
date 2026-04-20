package com.railway.train.repository;

import com.railway.train.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByTrainIdAndIsActiveTrue(Long trainId);
}
