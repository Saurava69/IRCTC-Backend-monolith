package com.railway.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CancellationResponse(
        String pnr,
        String bookingStatus,
        String cancellationReason,
        BigDecimal refundAmount,
        String refundStatus,
        Instant cancelledAt
) {}
