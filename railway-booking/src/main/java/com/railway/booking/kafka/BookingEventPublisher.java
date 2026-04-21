package com.railway.booking.kafka;

import com.railway.booking.entity.Booking;
import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    public void publishBookingInitiated(Booking booking) {
        publish(booking, "BOOKING_INITIATED");
    }

    public void publishBookingConfirmed(Booking booking) {
        publish(booking, "BOOKING_CONFIRMED");
    }

    public void publishBookingFailed(Booking booking) {
        publish(booking, "BOOKING_FAILED");
    }

    private void publish(Booking booking, String eventType) {
        BookingEvent payload = new BookingEvent(
                booking.getId(),
                booking.getPnr(),
                booking.getUserId(),
                booking.getTrainRunId(),
                booking.getCoachType(),
                booking.getFromStationId(),
                booking.getToStationId(),
                booking.getTotalFare(),
                booking.getPassengerCount(),
                booking.getBookingStatus().name(),
                booking.getIdempotencyKey()
        );

        EventEnvelope<BookingEvent> envelope = EventEnvelope.<BookingEvent>builder()
                .eventType(eventType)
                .aggregateId(String.valueOf(booking.getId()))
                .aggregateType("Booking")
                .source("railway-booking")
                .correlationId(booking.getPnr())
                .idempotencyKey(booking.getIdempotencyKey())
                .payload(payload)
                .build();

        kafkaTemplate.send(bookingEventsTopic, String.valueOf(booking.getTrainRunId()), envelope);
        log.info("Published {} for PNR {}", eventType, booking.getPnr());
    }
}
