package com.railway.booking.controller;

import com.railway.booking.dto.SeatAvailabilityResponse;
import com.railway.booking.ratelimit.RateLimit;
import com.railway.booking.service.SeatAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
@Tag(name = "6. Availability")
public class AvailabilityController {

    private final SeatAvailabilityService availabilityService;

    @GetMapping
    @RateLimit(requests = 30, windowSeconds = 60, keyPrefix = "search")
    @Operation(summary = "Check seat availability",
            description = """
                    Check available seats for a train run and coach type.
                    Optionally filter by segment (fromStation → toStation) for routes with intermediate stops.
                    Results are cached in Redis. Rate limited: 30 requests per minute per user.""")
    @ApiResponse(responseCode = "200", description = "Availability returned")
    @ApiResponse(responseCode = "404", description = "Train run or inventory not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<SeatAvailabilityResponse> checkAvailability(
            @Parameter(description = "Train run ID (from search results)", example = "1")
            @RequestParam Long trainRunId,
            @Parameter(description = "Coach type: FIRST_AC, SECOND_AC, THIRD_AC, SLEEPER, GENERAL", example = "SLEEPER")
            @RequestParam String coachType,
            @Parameter(description = "From station ID (optional, for segment availability)", example = "1")
            @RequestParam(required = false) Long fromStationId,
            @Parameter(description = "To station ID (optional, for segment availability)", example = "3")
            @RequestParam(required = false) Long toStationId) {

        SeatAvailabilityResponse response;
        if (fromStationId != null && toStationId != null) {
            response = availabilityService.getSegmentAvailability(
                    trainRunId, coachType, fromStationId, toStationId);
        } else {
            response = availabilityService.getAvailability(trainRunId, coachType);
        }

        return ResponseEntity.ok(response);
    }
}
