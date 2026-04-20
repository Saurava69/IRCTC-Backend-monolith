package com.railway.train.dto;

public record StationResponse(
        Long id,
        String code,
        String name,
        String city,
        String state,
        String zone
) {
}
