package com.railway.train.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStationRequest(
        @NotBlank @Size(max = 10) String code,
        @NotBlank String name,
        String city,
        String state,
        String zone,
        Double latitude,
        Double longitude
) {
}
