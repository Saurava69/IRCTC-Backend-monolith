package com.railway.booking.kafka;

import com.railway.booking.entity.Booking;
import com.railway.booking.entity.BookingStatus;
import com.railway.booking.redis.SeatLockManager;
import com.railway.booking.repository.BookingRepository;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.booking.service.PnrStatusService;
import com.railway.booking.service.SeatAvailabilityService;
import com.railway.common.event.EventEnvelope;
import com.railway.common.event.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatLockManager seatLockManager;
    private final SeatAvailabilityService availabilityService;
    private final PnrStatusService pnrStatusService;
    private final BookingEventPublisher bookingEventPublisher;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "booking-service")
    @Transactional
    public void handlePaymentEvent(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        log.info("Received payment event: {} for aggregate {}", eventType, envelope.getAggregateId());

        PaymentEvent event = objectMapper.convertValue(envelope.getPayload(), PaymentEvent.class);

        switch (eventType) {
            case "PAYMENT_SUCCESS" -> handlePaymentSuccess(event);
            case "PAYMENT_FAILED" -> handlePaymentFailed(event);
            default -> log.warn("Unknown payment event type: {}", eventType);
        }
    }

    private void handlePaymentSuccess(PaymentEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> {
                    log.error("Booking not found for payment event: {}", event.bookingId());
                    return new RuntimeException("Booking not found: " + event.bookingId());
                });

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            log.info("Booking {} already confirmed, skipping duplicate event", booking.getPnr());
            return;
        }

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setBookedAt(Instant.now());
        booking.getPassengers().forEach(p -> p.setStatus(BookingStatus.CONFIRMED));
        bookingRepository.save(booking);

        availabilityService.evictCache(booking.getTrainRunId(), booking.getCoachType());
        pnrStatusService.evictCache(booking.getPnr());

        bookingEventPublisher.publishBookingConfirmed(booking);

        log.info("Booking CONFIRMED: PNR={}, payment={}", booking.getPnr(), event.paymentId());
    }

    private void handlePaymentFailed(PaymentEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> {
                    log.error("Booking not found for payment event: {}", event.bookingId());
                    return new RuntimeException("Booking not found: " + event.bookingId());
                });

        if (booking.getBookingStatus() == BookingStatus.FAILED) {
            log.info("Booking {} already failed, skipping duplicate event", booking.getPnr());
            return;
        }

        booking.setBookingStatus(BookingStatus.FAILED);
        booking.getPassengers().forEach(p -> p.setStatus(BookingStatus.FAILED));
        bookingRepository.save(booking);

        seatInventoryRepository.findBySegment(
                booking.getTrainRunId(), booking.getCoachType(),
                booking.getFromStationId(), booking.getToStationId()
        ).ifPresent(inv -> seatInventoryRepository.incrementAvailableSeats(
                inv.getId(), booking.getPassengerCount(), inv.getVersion()));

        try {
            seatLockManager.releaseLock(
                    booking.getTrainRunId(), booking.getCoachType(),
                    booking.getFromStationId(), booking.getToStationId(),
                    booking.getPassengerCount(), booking.getPnr());
        } catch (Exception e) {
            log.warn("Could not release seat lock for PNR {}: {}", booking.getPnr(), e.getMessage());
        }

        availabilityService.evictCache(booking.getTrainRunId(), booking.getCoachType());
        pnrStatusService.evictCache(booking.getPnr());

        bookingEventPublisher.publishBookingFailed(booking);

        log.info("Booking FAILED due to payment failure: PNR={}, reason={}",
                booking.getPnr(), event.failureReason());
    }

    @DltHandler
    public void handleDlt(EventEnvelope<?> envelope) {
        log.error("Payment event sent to DLT after retries exhausted: eventType={}, aggregateId={}",
                envelope.getEventType(), envelope.getAggregateId());
    }
}
