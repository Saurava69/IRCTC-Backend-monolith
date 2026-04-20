package com.railway.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatAvailabilityResponse(
        Long trainRunId,
        String coachType,
        int totalSeats,
        int availableSeats,
        int racAvailable,
        int waitlistCount
) {
}
