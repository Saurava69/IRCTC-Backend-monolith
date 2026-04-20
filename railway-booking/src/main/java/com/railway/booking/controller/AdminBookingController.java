package com.railway.booking.controller;

import com.railway.booking.dto.GenerateTrainRunsRequest;
import com.railway.booking.service.TrainRunService;
import com.railway.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminBookingController {

    private final TrainRunService trainRunService;

    @PersistenceContext
    private EntityManager entityManager;

    @PostMapping("/train-runs/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> generateTrainRuns(
            @Valid @RequestBody GenerateTrainRunsRequest request) {

        @SuppressWarnings("unchecked")
        List<Object[]> schedules = entityManager.createNativeQuery(
                "SELECT s.id, s.route_id, s.runs_on_monday, s.runs_on_tuesday, " +
                "s.runs_on_wednesday, s.runs_on_thursday, s.runs_on_friday, " +
                "s.runs_on_saturday, s.runs_on_sunday FROM schedules s " +
                "WHERE s.train_id = :trainId AND s.is_active = true"
        ).setParameter("trainId", request.trainId()).getResultList();

        if (schedules.isEmpty()) {
            throw new ResourceNotFoundException("Schedule", request.trainId());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> coachData = entityManager.createNativeQuery(
                "SELECT DISTINCT c.coach_type, SUM(c.total_seats) as total " +
                "FROM coaches c WHERE c.train_id = :trainId GROUP BY c.coach_type"
        ).setParameter("trainId", request.trainId()).getResultList();

        int totalRuns = 0;
        for (Object[] schedule : schedules) {
            Long scheduleId = ((Number) schedule[0]).longValue();
            Long routeId = ((Number) schedule[1]).longValue();
            boolean[] runsOn = new boolean[7];
            for (int i = 0; i < 7; i++) {
                runsOn[i] = Boolean.TRUE.equals(schedule[i + 2]);
            }

            @SuppressWarnings("unchecked")
            List<Object[]> routeStations = entityManager.createNativeQuery(
                    "SELECT rs.station_id FROM route_stations rs " +
                    "WHERE rs.route_id = :routeId ORDER BY rs.sequence_number"
            ).setParameter("routeId", routeId).getResultList();

            List<TrainRunService.SegmentInfo> segments = new ArrayList<>();
            List<Long> stationIds = routeStations.stream()
                    .map(r -> ((Number) r[0]).longValue()).toList();

            for (Object[] coach : coachData) {
                String coachType = (String) coach[0];
                int totalSeats = ((Number) coach[1]).intValue();

                for (int i = 0; i < stationIds.size(); i++) {
                    for (int j = i + 1; j <= Math.min(i + 1, stationIds.size() - 1); j++) {
                        segments.add(new TrainRunService.SegmentInfo(
                                coachType, stationIds.get(i), stationIds.get(j), totalSeats));
                    }
                }
            }

            totalRuns += trainRunService.generateTrainRuns(
                    request.trainId(), scheduleId, routeId, runsOn,
                    request.fromDate(), request.toDate(), segments);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Train runs generated successfully",
                "totalRunsGenerated", totalRuns,
                "trainId", request.trainId(),
                "fromDate", request.fromDate().toString(),
                "toDate", request.toDate().toString()
        ));
    }
}
