package com.railway.common.event;

import java.math.BigDecimal;

public record PaymentEvent(
        Long paymentId,
        Long bookingId,
        String pnr,
        BigDecimal amount,
        String status,
        String gatewayTransactionId,
        String failureReason
) {}
