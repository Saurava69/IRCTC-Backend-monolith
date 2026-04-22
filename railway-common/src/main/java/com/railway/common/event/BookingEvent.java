package com.railway.common.event;

import java.math.BigDecimal;

public record BookingEvent(
        Long bookingId,
        String pnr,
        Long userId,
        Long trainRunId,
        String coachType,
        Long fromStationId,
        Long toStationId,
        BigDecimal totalFare,
        int passengerCount,
        String status,
        String idempotencyKey,
        String cancellationReason,
        String previousStatus
) {}
