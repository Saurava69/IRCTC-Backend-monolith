package com.railway.train.controller;

import com.railway.common.dto.PagedResponse;
import com.railway.train.dto.CreateStationRequest;
import com.railway.train.dto.StationResponse;
import com.railway.train.service.StationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    @GetMapping("/api/v1/stations")
    public ResponseEntity<PagedResponse<StationResponse>> search(
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(stationService.search(q, pageable)));
    }

    @GetMapping("/api/v1/stations/{code}")
    public ResponseEntity<StationResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(stationService.getByCode(code));
    }

    @PostMapping("/api/v1/admin/stations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationService.create(request));
    }
}
