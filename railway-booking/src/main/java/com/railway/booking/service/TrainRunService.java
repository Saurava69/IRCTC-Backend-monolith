package com.railway.booking.service;

import com.railway.booking.entity.SeatInventory;
import com.railway.booking.entity.TrainRun;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.booking.repository.TrainRunRepository;
import com.railway.common.exception.BusinessException;
import com.railway.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainRunService {

    private final TrainRunRepository trainRunRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    @Transactional
    public int generateTrainRuns(Long trainId, Long scheduleId, Long routeId,
                                 boolean[] runsOnDays, LocalDate from, LocalDate to,
                                 List<SegmentInfo> segments) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!runsOnDay(runsOnDays, date.getDayOfWeek())) continue;
            if (trainRunRepository.existsByScheduleIdAndRunDate(scheduleId, date)) continue;

            TrainRun run = TrainRun.builder()
                    .scheduleId(scheduleId)
                    .trainId(trainId)
                    .routeId(routeId)
                    .runDate(date)
                    .build();
            run = trainRunRepository.save(run);

            createSeatInventory(run.getId(), segments);
            count++;
        }

        log.info("Generated {} train runs for train {} from {} to {}", count, trainId, from, to);
        return count;
    }

    public TrainRun getTrainRun(Long trainRunId) {
        return trainRunRepository.findById(trainRunId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainRun", trainRunId));
    }

    public TrainRun getTrainRun(Long trainId, LocalDate date) {
        return trainRunRepository.findByTrainIdAndRunDate(trainId, date)
                .orElseThrow(() -> new BusinessException("TRAIN_RUN_NOT_FOUND",
                        "No train run found for train " + trainId + " on " + date));
    }

    private void createSeatInventory(Long trainRunId, List<SegmentInfo> segments) {
        List<SeatInventory> inventories = new ArrayList<>();
        for (SegmentInfo seg : segments) {
            SeatInventory inv = SeatInventory.builder()
                    .trainRunId(trainRunId)
                    .coachType(seg.coachType())
                    .fromStationId(seg.fromStationId())
                    .toStationId(seg.toStationId())
                    .totalSeats(seg.totalSeats())
                    .availableSeats(seg.totalSeats())
                    .racSeats(0)
                    .waitlistCount(0)
                    .build();
            inventories.add(inv);
        }
        seatInventoryRepository.saveAll(inventories);
    }

    private boolean runsOnDay(boolean[] runsOnDays, DayOfWeek day) {
        return runsOnDays[day.getValue() - 1];
    }

    public record SegmentInfo(String coachType, Long fromStationId, Long toStationId, int totalSeats) {
    }
}
