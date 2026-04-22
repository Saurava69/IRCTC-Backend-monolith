package com.railway.train.scheduler;

import com.railway.common.scheduler.ScheduleDataProvider;
import com.railway.train.entity.*;
import com.railway.train.repository.CoachRepository;
import com.railway.train.repository.RouteRepository;
import com.railway.train.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleDataProviderImpl implements ScheduleDataProvider {

    private final ScheduleRepository scheduleRepository;
    private final RouteRepository routeRepository;
    private final CoachRepository coachRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleInfo> getAllActiveSchedulesWithSegments() {
        List<Schedule> schedules = scheduleRepository.findAllActiveWithTrainAndRoute();
        List<ScheduleInfo> result = new ArrayList<>();

        for (Schedule schedule : schedules) {
            Long trainId = schedule.getTrain().getId();
            Long routeId = schedule.getRoute().getId();

            boolean[] runsOnDays = {
                    Boolean.TRUE.equals(schedule.getRunsOnMonday()),
                    Boolean.TRUE.equals(schedule.getRunsOnTuesday()),
                    Boolean.TRUE.equals(schedule.getRunsOnWednesday()),
                    Boolean.TRUE.equals(schedule.getRunsOnThursday()),
                    Boolean.TRUE.equals(schedule.getRunsOnFriday()),
                    Boolean.TRUE.equals(schedule.getRunsOnSaturday()),
                    Boolean.TRUE.equals(schedule.getRunsOnSunday())
            };

            Optional<Route> routeOpt = routeRepository.findByIdWithStations(routeId);
            if (routeOpt.isEmpty()) {
                log.warn("Route {} not found for schedule {}, skipping", routeId, schedule.getId());
                continue;
            }

            List<RouteStation> stations = routeOpt.get().getRouteStations().stream()
                    .sorted(Comparator.comparingInt(RouteStation::getSequenceNumber))
                    .toList();

            List<Long> stationIds = stations.stream()
                    .map(rs -> rs.getStation().getId())
                    .toList();

            List<Coach> coaches = coachRepository.findByTrainIdOrderBySequenceInTrain(trainId);
            Map<String, Integer> seatsByCoachType = coaches.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.getCoachType().name(),
                            Collectors.summingInt(Coach::getTotalSeats)));

            List<SegmentTemplate> segments = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : seatsByCoachType.entrySet()) {
                String coachType = entry.getKey();
                int totalSeats = entry.getValue();

                for (int i = 0; i < stationIds.size(); i++) {
                    for (int j = i + 1; j <= Math.min(i + 1, stationIds.size() - 1); j++) {
                        segments.add(new SegmentTemplate(
                                coachType, stationIds.get(i), stationIds.get(j), totalSeats));
                    }
                }
            }

            result.add(new ScheduleInfo(schedule.getId(), trainId, routeId, runsOnDays, segments));
        }

        log.info("Loaded {} active schedules with segments", result.size());
        return result;
    }
}
