package com.railway.train.controller;

import com.railway.train.dto.CreateTrainRequest;
import com.railway.train.dto.TrainResponse;
import com.railway.train.service.TrainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;

    @GetMapping("/api/v1/trains")
    @Tag(name = "4. Trains")
    @Operation(summary = "List all trains",
            description = "Returns all active trains with their coach composition. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Trains list returned")
    public ResponseEntity<List<TrainResponse>> getAll() {
        return ResponseEntity.ok(trainService.getAllActive());
    }

    @GetMapping("/api/v1/trains/{trainNumber}")
    @Tag(name = "4. Trains")
    @Operation(summary = "Get train by number",
            description = "Lookup a train by its number (e.g. 12301). Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Train found")
    @ApiResponse(responseCode = "404", description = "Train not found")
    public ResponseEntity<TrainResponse> getByTrainNumber(
            @Parameter(description = "Train number", example = "12301")
            @PathVariable String trainNumber) {
        return ResponseEntity.ok(trainService.getByTrainNumber(trainNumber));
    }

    @PostMapping("/api/v1/admin/trains")
    @PreAuthorize("hasRole('ADMIN')")
    @Tag(name = "10. Admin - Trains")
    @Operation(summary = "Create a new train (Admin)",
            description = "Add a new train with coach composition to the system.")
    @ApiResponse(responseCode = "201", description = "Train created")
    @ApiResponse(responseCode = "409", description = "Train number already exists")
    public ResponseEntity<TrainResponse> create(@Valid @RequestBody CreateTrainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainService.create(request));
    }
}
