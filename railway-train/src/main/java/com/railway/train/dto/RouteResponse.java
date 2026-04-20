package com.railway.train.dto;

import java.time.LocalTime;
import java.util.List;

public record RouteResponse(
        Long id,
        String routeName,
        Long trainId,
        String trainNumber,
        List<RouteStationResponse> stations
) {
    public record RouteStationResponse(
            Long id,
            StationResponse station,
            int sequenceNumber,
            LocalTime arrivalTime,
            LocalTime departureTime,
            int haltMinutes,
            int distanceFromOriginKm,
            int dayOffset
    ) {
    }
}
