package com.railway.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class MockPaymentGateway {

    public GatewayResponse processPayment(Long bookingId, java.math.BigDecimal amount, String paymentMethod) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean success = ThreadLocalRandom.current().nextInt(100) < 90;
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        if (success) {
            log.info("Mock gateway: Payment SUCCESS for booking {} amount {} via {}",
                    bookingId, amount, paymentMethod);
            return new GatewayResponse(true, transactionId, "Payment processed successfully", null);
        } else {
            String reason = "Insufficient funds";
            log.info("Mock gateway: Payment FAILED for booking {} - {}", bookingId, reason);
            return new GatewayResponse(false, transactionId, null, reason);
        }
    }

    public GatewayResponse processRefund(Long bookingId, java.math.BigDecimal amount) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(30, 150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean success = ThreadLocalRandom.current().nextInt(100) < 95;
        String transactionId = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        if (success) {
            log.info("Mock gateway: Refund SUCCESS for booking {} amount {}", bookingId, amount);
            return new GatewayResponse(true, transactionId, "Refund processed successfully", null);
        } else {
            log.warn("Mock gateway: Refund FAILED for booking {}", bookingId);
            return new GatewayResponse(false, transactionId, null, "Refund gateway error");
        }
    }

    public record GatewayResponse(
            boolean success,
            String transactionId,
            String message,
            String failureReason
    ) {}
}
