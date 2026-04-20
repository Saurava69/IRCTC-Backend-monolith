package com.railway.train.dto;

import com.railway.train.entity.CoachType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateTrainRequest(
        @NotBlank String trainNumber,
        @NotBlank String name,
        @NotBlank String trainType,
        @NotNull Long sourceStationId,
        @NotNull Long destStationId,
        List<CoachRequest> coaches
) {
    public record CoachRequest(
            @NotBlank String coachNumber,
            @NotNull CoachType coachType,
            @Positive int totalSeats,
            @Positive int totalBerths,
            @Positive int sequenceInTrain
    ) {
    }
}
