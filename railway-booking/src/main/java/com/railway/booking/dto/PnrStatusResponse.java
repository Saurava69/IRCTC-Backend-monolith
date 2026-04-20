package com.railway.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PnrStatusResponse(
        String pnr,
        String bookingStatus,
        Long trainRunId,
        String coachType,
        Long fromStationId,
        Long toStationId,
        List<PassengerStatus> passengers
) {
    public record PassengerStatus(
            String name,
            int age,
            String status,
            String seatNumber,
            String coachNumber,
            Integer waitlistNumber,
            Integer racNumber
    ) {
    }
}
