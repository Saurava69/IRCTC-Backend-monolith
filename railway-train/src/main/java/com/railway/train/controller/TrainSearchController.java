package com.railway.train.controller;

import com.railway.train.dto.JourneySearchResponse;
import com.railway.train.dto.StationSuggestionResponse;
import com.railway.train.search.service.TrainSearchService;
import com.railway.train.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrainSearchController {

    private final TrainSearchService trainSearchService;

    @GetMapping("/api/v1/trains/search")
    public ResponseEntity<List<JourneySearchResponse>> searchTrains(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String coachType) {
        List<JourneySearchResponse> results = trainSearchService.searchTrainsAdvanced(from, to, date, coachType);
        return ResponseEntity.ok(results);
    }
}
