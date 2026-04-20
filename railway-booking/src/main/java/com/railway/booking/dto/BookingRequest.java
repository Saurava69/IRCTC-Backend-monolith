package com.railway.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record BookingRequest(
        @NotNull Long trainRunId,
        @NotBlank String coachType,
        @NotNull Long fromStationId,
        @NotNull Long toStationId,
        @NotEmpty @Size(max = 6) List<@Valid PassengerRequest> passengers,
        String idempotencyKey
) {
    public record PassengerRequest(
            @NotBlank String name,
            @Min(1) @Max(120) int age,
            @NotBlank String gender,
            String berthPreference
    ) {
    }
}
