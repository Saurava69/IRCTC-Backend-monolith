package com.railway.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateTrainRunsRequest(
        @NotNull Long trainId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}
