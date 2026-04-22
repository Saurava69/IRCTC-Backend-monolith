package com.railway.payment.kafka;

import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import com.railway.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCancelledConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "payment-refund-service")
    @Transactional
    public void handleBookingEvent(EventEnvelope<?> envelope) {
        if (!"BOOKING_CANCELLED".equals(envelope.getEventType())) {
            return;
        }

        BookingEvent event = objectMapper.convertValue(envelope.getPayload(), BookingEvent.class);
        log.info("Received BOOKING_CANCELLED for PNR {}, initiating refund", event.pnr());

        paymentService.initiateRefund(event.bookingId());
    }

    @DltHandler
    public void handleDlt(EventEnvelope<?> envelope) {
        log.error("Booking event sent to DLT after retries: eventType={}, aggregateId={}",
                envelope.getEventType(), envelope.getAggregateId());
    }
}
