package com.railway.train.controller;

import com.railway.train.dto.JourneySearchResponse;
import com.railway.train.search.service.TrainSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "5. Train Search")
public class TrainSearchController {

    private final TrainSearchService trainSearchService;

    @GetMapping("/api/v1/trains/search")
    @Operation(summary = "Search trains between stations",
            description = """
                    Search for available trains between two stations on a given date.
                    Uses Elasticsearch for fast full-text search. Supports station codes (NDLS) or names (New Delhi).
                    Public endpoint — no auth required.""")
    @ApiResponse(responseCode = "200", description = "Search results returned (may be empty)")
    public ResponseEntity<List<JourneySearchResponse>> searchTrains(
            @Parameter(description = "From station (code or name)", example = "NDLS")
            @RequestParam String from,
            @Parameter(description = "To station (code or name)", example = "BCT")
            @RequestParam String to,
            @Parameter(description = "Travel date (yyyy-MM-dd)", example = "2026-05-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Filter by coach type: FIRST_AC, SECOND_AC, THIRD_AC, SLEEPER, GENERAL")
            @RequestParam(required = false) String coachType) {
        List<JourneySearchResponse> results = trainSearchService.searchTrainsAdvanced(from, to, date, coachType);
        return ResponseEntity.ok(results);
    }
}
