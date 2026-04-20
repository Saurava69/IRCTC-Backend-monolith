package com.railway.train.controller;

import com.railway.train.dto.CreateTrainRequest;
import com.railway.train.dto.TrainResponse;
import com.railway.train.service.TrainService;
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
    public ResponseEntity<List<TrainResponse>> getAll() {
        return ResponseEntity.ok(trainService.getAllActive());
    }

    @GetMapping("/api/v1/trains/{trainNumber}")
    public ResponseEntity<TrainResponse> getByTrainNumber(@PathVariable String trainNumber) {
        return ResponseEntity.ok(trainService.getByTrainNumber(trainNumber));
    }

    @PostMapping("/api/v1/admin/trains")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrainResponse> create(@Valid @RequestBody CreateTrainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainService.create(request));
    }
}
