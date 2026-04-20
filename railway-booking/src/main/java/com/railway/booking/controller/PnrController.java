package com.railway.booking.controller;

import com.railway.booking.dto.PnrStatusResponse;
import com.railway.booking.ratelimit.RateLimit;
import com.railway.booking.service.PnrStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pnr")
@RequiredArgsConstructor
public class PnrController {

    private final PnrStatusService pnrStatusService;

    @GetMapping("/{pnr}")
    @RateLimit(requests = 20, windowSeconds = 60, keyPrefix = "pnr")
    public ResponseEntity<PnrStatusResponse> checkStatus(@PathVariable String pnr) {
        return ResponseEntity.ok(pnrStatusService.getStatus(pnr));
    }
}
