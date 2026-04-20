package com.railway.train.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;
import java.util.List;

public record CreateRouteRequest(
        @NotNull Long trainId,
        String routeName,
        List<RouteStationRequest> stations
) {
    public record RouteStationRequest(
            @NotNull Long stationId,
            @Positive int sequenceNumber,
            LocalTime arrivalTime,
            LocalTime departureTime,
            int haltMinutes,
            int distanceFromOriginKm,
            int dayOffset
    ) {
    }
}
