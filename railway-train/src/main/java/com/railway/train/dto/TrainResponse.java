package com.railway.train.dto;

import java.util.List;

public record TrainResponse(
        Long id,
        String trainNumber,
        String name,
        String trainType,
        StationResponse sourceStation,
        StationResponse destStation,
        List<CoachResponse> coaches
) {
    public record CoachResponse(
            Long id,
            String coachNumber,
            String coachType,
            int totalSeats,
            int totalBerths,
            int sequenceInTrain
    ) {
    }
}
