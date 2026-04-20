package com.railway.booking.controller;

import com.railway.booking.dto.SeatAvailabilityResponse;
import com.railway.booking.ratelimit.RateLimit;
import com.railway.booking.service.SeatAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final SeatAvailabilityService availabilityService;

    @GetMapping
    @RateLimit(requests = 30, windowSeconds = 60, keyPrefix = "search")
    public ResponseEntity<SeatAvailabilityResponse> checkAvailability(
            @RequestParam Long trainRunId,
            @RequestParam String coachType,
            @RequestParam(required = false) Long fromStationId,
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
