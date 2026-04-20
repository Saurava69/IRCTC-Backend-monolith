package com.railway.train.controller;

import com.railway.train.dto.CreateRouteRequest;
import com.railway.train.dto.CreateScheduleRequest;
import com.railway.train.dto.RouteResponse;
import com.railway.train.dto.ScheduleResponse;
import com.railway.train.service.RouteService;
import com.railway.train.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final ScheduleService scheduleService;

    @GetMapping("/api/v1/routes/{id}")
    public ResponseEntity<RouteResponse> getRoute(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.getById(id));
    }

    @GetMapping("/api/v1/trains/{trainId}/routes")
    public ResponseEntity<List<RouteResponse>> getRoutesByTrain(@PathVariable Long trainId) {
        return ResponseEntity.ok(routeService.getByTrainId(trainId));
    }

    @PostMapping("/api/v1/admin/routes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(request));
    }

    @GetMapping("/api/v1/trains/{trainId}/schedules")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(@PathVariable Long trainId) {
        return ResponseEntity.ok(scheduleService.getByTrainId(trainId));
    }

    @PostMapping("/api/v1/admin/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }
}
