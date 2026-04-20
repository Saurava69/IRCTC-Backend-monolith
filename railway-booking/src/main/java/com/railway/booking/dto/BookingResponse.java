package com.railway.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id,
        String pnr,
        String bookingStatus,
        Long trainRunId,
        String coachType,
        Long fromStationId,
        Long toStationId,
        BigDecimal totalFare,
        int passengerCount,
        List<PassengerResponse> passengers,
        Instant createdAt
) {
    public record PassengerResponse(
            Long id,
            String name,
            int age,
            String gender,
            String berthPreference,
            String seatNumber,
            String coachNumber,
            String status,
            Integer waitlistNumber,
            Integer racNumber
    ) {
    }
}
