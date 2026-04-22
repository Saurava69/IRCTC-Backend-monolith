package com.railway.booking.controller;

import com.railway.booking.dto.PnrStatusResponse;
import com.railway.booking.ratelimit.RateLimit;
import com.railway.booking.service.PnrStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pnr")
@RequiredArgsConstructor
@Tag(name = "8. PNR Status")
public class PnrController {

    private final PnrStatusService pnrStatusService;

    @GetMapping("/{pnr}")
    @RateLimit(requests = 20, windowSeconds = 60, keyPrefix = "pnr")
    @Operation(summary = "Check PNR status",
            description = """
                    Check the current status of a booking by PNR number.
                    Returns booking status, passenger details with individual seat/berth assignments,
                    and current position for RAC/waitlisted passengers.
                    Public endpoint — no auth required. Rate limited: 20 requests per minute.""")
    @ApiResponse(responseCode = "200", description = "PNR status returned")
    @ApiResponse(responseCode = "404", description = "PNR not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<PnrStatusResponse> checkStatus(
            @Parameter(description = "PNR number", example = "PNR2026042200001")
            @PathVariable String pnr) {
        return ResponseEntity.ok(pnrStatusService.getStatus(pnr));
    }
}
