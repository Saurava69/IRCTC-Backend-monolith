package com.railway.train.controller;

import com.railway.common.dto.PagedResponse;
import com.railway.train.dto.CreateStationRequest;
import com.railway.train.dto.StationResponse;
import com.railway.train.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
    @Tag(name = "3. Stations")
    @Operation(summary = "Search stations",
            description = "Search stations by name or code. Returns paginated results. Public endpoint — no auth required.")
    @ApiResponse(responseCode = "200", description = "Stations list returned")
    public ResponseEntity<PagedResponse<StationResponse>> search(
            @Parameter(description = "Search query (station name or code)", example = "Delhi")
            @RequestParam(defaultValue = "") String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(stationService.search(q, pageable)));
    }

    @GetMapping("/api/v1/stations/{code}")
    @Tag(name = "3. Stations")
    @Operation(summary = "Get station by code",
            description = "Lookup a single station by its code (e.g. NDLS, BCT). Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Station found")
    @ApiResponse(responseCode = "404", description = "Station not found")
    public ResponseEntity<StationResponse> getByCode(
            @Parameter(description = "Station code", example = "NDLS")
            @PathVariable String code) {
        return ResponseEntity.ok(stationService.getByCode(code));
    }

    @PostMapping("/api/v1/admin/stations")
    @PreAuthorize("hasRole('ADMIN')")
    @Tag(name = "10. Admin - Trains")
    @Operation(summary = "Create a new station (Admin)",
            description = "Add a new railway station to the system.")
    @ApiResponse(responseCode = "201", description = "Station created")
    @ApiResponse(responseCode = "409", description = "Station code already exists")
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationService.create(request));
    }
}
