package com.railway.train.service;

import com.railway.common.exception.ResourceNotFoundException;
import com.railway.train.dto.CreateScheduleRequest;
import com.railway.train.dto.ScheduleResponse;
import com.railway.train.entity.Route;
import com.railway.train.entity.Schedule;
import com.railway.train.entity.Train;
import com.railway.train.repository.RouteRepository;
import com.railway.train.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final TrainService trainService;
    private final RouteRepository routeRepository;

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        Train train = trainService.getEntityById(request.trainId());
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new ResourceNotFoundException("Route", request.routeId()));

        Schedule schedule = Schedule.builder()
                .train(train)
                .route(route)
                .runsOnMonday(request.runsOnMonday())
                .runsOnTuesday(request.runsOnTuesday())
                .runsOnWednesday(request.runsOnWednesday())
                .runsOnThursday(request.runsOnThursday())
                .runsOnFriday(request.runsOnFriday())
                .runsOnSaturday(request.runsOnSaturday())
                .runsOnSunday(request.runsOnSunday())
                .effectiveFrom(request.effectiveFrom())
                .effectiveUntil(request.effectiveUntil())
                .build();

        schedule = scheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getByTrainId(Long trainId) {
        return scheduleRepository.findByTrainIdAndIsActiveTrue(trainId).stream()
                .map(this::toResponse).toList();
    }

    private ScheduleResponse toResponse(Schedule s) {
        return new ScheduleResponse(
                s.getId(),
                s.getTrain().getId(),
                s.getTrain().getTrainNumber(),
                s.getRoute().getId(),
                s.getRunsOnMonday(),
                s.getRunsOnTuesday(),
                s.getRunsOnWednesday(),
                s.getRunsOnThursday(),
                s.getRunsOnFriday(),
                s.getRunsOnSaturday(),
                s.getRunsOnSunday(),
                s.getEffectiveFrom(),
                s.getEffectiveUntil(),
                s.getIsActive()
        );
    }
}
