package com.railway.app.controller;

import com.railway.booking.scheduler.BookingCleanupJob;
import com.railway.booking.scheduler.StaleDataCleanupJob;
import com.railway.booking.scheduler.TrainRunGenerationJob;
import com.railway.train.search.scheduler.SearchIndexRefreshJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/scheduler")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "13. Admin - Scheduler")
public class AdminSchedulerController {

    private final BookingCleanupJob bookingCleanupJob;
    private final TrainRunGenerationJob trainRunGenerationJob;
    private final SearchIndexRefreshJob searchIndexRefreshJob;
    private final StaleDataCleanupJob staleDataCleanupJob;

    @PostMapping("/trigger/{jobName}")
    @Operation(summary = "Trigger a scheduled job manually (Admin)",
            description = """
                    Manually trigger a scheduled job for testing or recovery.
                    Available jobs: booking-cleanup, train-run-generation, search-reindex, stale-cleanup""")
    @ApiResponse(responseCode = "200", description = "Job triggered successfully")
    @ApiResponse(responseCode = "400", description = "Unknown job name")
    public ResponseEntity<Map<String, Object>> triggerJob(
            @Parameter(description = "Job name", example = "booking-cleanup")
            @PathVariable String jobName) {

        switch (jobName) {
            case "booking-cleanup" -> bookingCleanupJob.cleanupExpiredBookings();
            case "train-run-generation" -> trainRunGenerationJob.generateUpcomingTrainRuns();
            case "search-reindex" -> searchIndexRefreshJob.nightlyReindex();
            case "stale-cleanup" -> staleDataCleanupJob.cleanupStaleTrainRuns();
            default -> {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unknown job: " + jobName,
                        "availableJobs", "booking-cleanup, train-run-generation, search-reindex, stale-cleanup"));
            }
        }

        return ResponseEntity.ok(Map.of(
                "job", jobName,
                "message", "Triggered successfully",
                "timestamp", Instant.now().toString()));
    }
}
