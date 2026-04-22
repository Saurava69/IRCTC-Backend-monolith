package com.railway.train.dto;

public record StationSuggestionResponse(
        Long id,
        String code,
        String name,
        String city
) {}
