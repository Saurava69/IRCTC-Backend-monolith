package com.railway.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long bookingId,
        String pnr,
        BigDecimal amount,
        String paymentStatus,
        String paymentMethod,
        String gatewayTransactionId,
        String failureReason,
        Instant createdAt
) {}
