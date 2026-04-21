package com.railway.notification.kafka;

import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final ObjectMapper objectMapper;

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

            default -> log.debug("[NOTIFICATION] Received booking event: {}", eventType);
        }
    }
}
