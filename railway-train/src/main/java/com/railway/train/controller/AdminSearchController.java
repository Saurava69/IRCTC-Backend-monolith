package com.railway.train.controller;

import com.railway.train.search.service.TrainSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private final TrainSearchService trainSearchService;

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reindex() {
        long count = trainSearchService.reindexAll();
        return ResponseEntity.ok(Map.of(
                "message", "Reindex completed successfully",
                "documentsIndexed", count
        ));
    }
}
