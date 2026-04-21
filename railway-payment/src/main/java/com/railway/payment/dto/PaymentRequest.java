package com.railway.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull Long bookingId,
        @NotBlank String paymentMethod,
        String idempotencyKey
) {}
