package com.railway.train.controller;

import com.railway.train.dto.CreateRouteRequest;
import com.railway.train.dto.CreateScheduleRequest;
import com.railway.train.dto.RouteResponse;
import com.railway.train.dto.ScheduleResponse;
import com.railway.train.service.RouteService;
import com.railway.train.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Tag(name = "4. Trains")
    @Operation(summary = "Get route details",
            description = "Returns route with all stops, distances, and timings.")
    @ApiResponse(responseCode = "200", description = "Route found")
    @ApiResponse(responseCode = "404", description = "Route not found")
    public ResponseEntity<RouteResponse> getRoute(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.getById(id));
    }

    @GetMapping("/api/v1/trains/{trainId}/routes")
    @Tag(name = "4. Trains")
    @Operation(summary = "Get routes for a train",
            description = "Returns all routes assigned to a train. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Routes list returned")
    public ResponseEntity<List<RouteResponse>> getRoutesByTrain(
            @Parameter(description = "Train ID", example = "1")
            @PathVariable Long trainId) {
        return ResponseEntity.ok(routeService.getByTrainId(trainId));
    }

    @PostMapping("/api/v1/admin/routes")
    @PreAuthorize("hasRole('ADMIN')")
    @Tag(name = "10. Admin - Trains")
    @Operation(summary = "Create a route (Admin)",
            description = "Create a route for a train with ordered station stops, timings, and distances.")
    @ApiResponse(responseCode = "201", description = "Route created")
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(request));
    }

    @GetMapping("/api/v1/trains/{trainId}/schedules")
    @Tag(name = "4. Trains")
    @Operation(summary = "Get schedules for a train",
            description = "Returns which days the train runs and the effective date range. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Schedules list returned")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(
            @Parameter(description = "Train ID", example = "1")
            @PathVariable Long trainId) {
        return ResponseEntity.ok(scheduleService.getByTrainId(trainId));
    }

    @PostMapping("/api/v1/admin/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    @Tag(name = "10. Admin - Trains")
    @Operation(summary = "Create a schedule (Admin)",
            description = "Define which days a train runs on a route, with effective date range.")
    @ApiResponse(responseCode = "201", description = "Schedule created")
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }
}
