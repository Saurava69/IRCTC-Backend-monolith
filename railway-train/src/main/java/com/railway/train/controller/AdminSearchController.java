package com.railway.train.controller;

import com.railway.train.search.service.TrainSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "12. Admin - Search")
public class AdminSearchController {

    private final TrainSearchService trainSearchService;

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rebuild Elasticsearch index (Admin)",
            description = "Deletes all documents from the journey_options index and re-indexes from PostgreSQL. Run this after data changes or to fix a stale index.")
    @ApiResponse(responseCode = "200", description = "Reindex completed, document count returned")
    public ResponseEntity<Map<String, Object>> reindex() {
        long count = trainSearchService.reindexAll();
        return ResponseEntity.ok(Map.of(
                "message", "Reindex completed successfully",
                "documentsIndexed", count
        ));
    }
}
