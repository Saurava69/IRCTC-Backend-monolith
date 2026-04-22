package com.railway.train.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record JourneySearchResponse(
        Long trainRunId,
        Long trainId,
        String trainNumber,
        String trainName,
        String trainType,
        LocalDate runDate,
        StationInfo fromStation,
        StationInfo toStation,
        String departureTime,
        String arrivalTime,
        Integer durationMinutes,
        Integer distanceKm,
        List<CoachAvailabilityInfo> availability,
        List<FareDetail> fares
) {
    public record StationInfo(Long id, String code, String name) {}

    public record CoachAvailabilityInfo(String coachType, int totalSeats, int availableSeats,
                                        int racSeats, int waitlistCount) {}

    public record FareDetail(String coachType, BigDecimal baseFare) {}
}
