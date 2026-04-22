package com.railway.notification.kafka;

import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import com.railway.common.event.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "notification-service")
    public void handleBookingEvent(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        BookingEvent event = objectMapper.convertValue(envelope.getPayload(), BookingEvent.class);

        switch (eventType) {
            case "BOOKING_INITIATED" -> log.info(
                    "[NOTIFICATION] Booking initiated — PNR: {}, User: {}, TrainRun: {}, Fare: {}",
                    event.pnr(), event.userId(), event.trainRunId(), event.totalFare());

            case "BOOKING_CONFIRMED" -> log.info(
                    "[NOTIFICATION] Sending confirmation email/SMS — PNR: {}, User: {}, Status: CONFIRMED",
                    event.pnr(), event.userId());

            case "BOOKING_FAILED" -> log.info(
                    "[NOTIFICATION] Sending failure notification — PNR: {}, User: {}, Status: FAILED",
                    event.pnr(), event.userId());

            case "BOOKING_CANCELLED" -> log.info(
                    "[NOTIFICATION] Booking cancelled — PNR: {}, User: {}, Reason: {}",
                    event.pnr(), event.userId(), event.cancellationReason());

            case "BOOKING_PROMOTED" -> log.info(
                    "[NOTIFICATION] Booking promoted — PNR: {}, User: {}, NewStatus: {}",
                    event.pnr(), event.userId(), event.status());

            default -> log.debug("[NOTIFICATION] Received booking event: {}", eventType);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "notification-payment-service")
    public void handlePaymentEvent(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        PaymentEvent event = objectMapper.convertValue(envelope.getPayload(), PaymentEvent.class);

        switch (eventType) {
            case "PAYMENT_SUCCESS" -> log.info(
                    "[NOTIFICATION] Payment successful — PNR: {}, Amount: {}",
                    event.pnr(), event.amount());

            case "PAYMENT_REFUNDED" -> log.info(
                    "[NOTIFICATION] Refund processed — PNR: {}, Amount: {}, TxnId: {}",
                    event.pnr(), event.amount(), event.gatewayTransactionId());

            default -> log.debug("[NOTIFICATION] Received payment event: {}", eventType);
        }
    }

    @DltHandler
    public void handleDlt(EventEnvelope<?> envelope) {
        log.error("[NOTIFICATION] Event failed after retries — type={}, aggregateId={}",
                envelope.getEventType(), envelope.getAggregateId());
    }
}
