package com.railway.booking.kafka;

import com.railway.booking.entity.Booking;
import com.railway.booking.entity.BookingStatus;
import com.railway.booking.entity.SeatInventory;
import com.railway.booking.repository.BookingRepository;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.booking.service.PnrStatusService;
import com.railway.booking.service.SeatAvailabilityService;
import com.railway.common.event.BookingEvent;
import com.railway.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistPromotionConsumer {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final SeatAvailabilityService availabilityService;
    private final PnrStatusService pnrStatusService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "booking-waitlist-service")
    @Transactional
    public void handleBookingEvent(EventEnvelope<?> envelope) {
        if (!"BOOKING_CANCELLED".equals(envelope.getEventType())) {
            return;
        }

        String idempotencyKey = "promotion:" + envelope.getEventId();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(acquired)) {
            log.info("Promotion already processed for eventId={}", envelope.getEventId());
            return;
        }

        BookingEvent event = objectMapper.convertValue(envelope.getPayload(), BookingEvent.class);
        log.info("Processing waitlist promotion for cancelled PNR {}, previousStatus={}",
                event.pnr(), event.previousStatus());

        attemptPromotion(event);
    }

    private void attemptPromotion(BookingEvent cancelledEvent) {
        String previousStatus = cancelledEvent.previousStatus();
        if (previousStatus == null) {
            log.warn("No previousStatus in cancellation event for booking {}", cancelledEvent.bookingId());
            return;
        }

        switch (previousStatus) {
            case "CONFIRMED" -> {
                promoteRacToConfirmed(cancelledEvent);
                promoteWaitlistedToRac(cancelledEvent);
            }
            case "RAC" -> promoteWaitlistedToRac(cancelledEvent);
            case "WAITLISTED" -> log.info("Waitlisted booking cancelled, no promotion needed");
            default -> log.warn("Unexpected previousStatus: {}", previousStatus);
        }
    }

    private void promoteRacToConfirmed(BookingEvent cancelledEvent) {
        List<Booking> racBookings = bookingRepository.findBySegmentAndStatus(
                cancelledEvent.trainRunId(), cancelledEvent.coachType(),
                cancelledEvent.fromStationId(), cancelledEvent.toStationId(),
                BookingStatus.RAC);

        if (racBookings.isEmpty()) {
            log.info("No RAC bookings to promote for trainRun={}", cancelledEvent.trainRunId());
            return;
        }

        Booking racBooking = racBookings.get(0);

        racBooking.setBookingStatus(BookingStatus.CONFIRMED);
        racBooking.setBookedAt(Instant.now());
        racBooking.getPassengers().forEach(p -> {
            p.setStatus(BookingStatus.CONFIRMED);
            p.setRacNumber(null);
        });
        bookingRepository.save(racBooking);

        seatInventoryRepository.findBySegment(
                cancelledEvent.trainRunId(), cancelledEvent.coachType(),
                cancelledEvent.fromStationId(), cancelledEvent.toStationId()
        ).ifPresent(inv -> seatInventoryRepository.decrementRacSeats(
                inv.getId(), racBooking.getPassengerCount(), inv.getVersion()));

        availabilityService.evictCache(racBooking.getTrainRunId(), racBooking.getCoachType());
        pnrStatusService.evictCache(racBooking.getPnr());

        bookingEventPublisher.publishBookingPromoted(racBooking);
        log.info("Promoted RAC→CONFIRMED: PNR={}", racBooking.getPnr());
    }

    private void promoteWaitlistedToRac(BookingEvent cancelledEvent) {
        List<Booking> waitlistedBookings = bookingRepository.findBySegmentAndStatus(
                cancelledEvent.trainRunId(), cancelledEvent.coachType(),
                cancelledEvent.fromStationId(), cancelledEvent.toStationId(),
                BookingStatus.WAITLISTED);

        if (waitlistedBookings.isEmpty()) {
            log.info("No waitlisted bookings to promote for trainRun={}", cancelledEvent.trainRunId());
            return;
        }

        Booking wlBooking = waitlistedBookings.get(0);

        wlBooking.setBookingStatus(BookingStatus.RAC);
        wlBooking.getPassengers().forEach(p -> {
            p.setStatus(BookingStatus.RAC);
            p.setWaitlistNumber(null);
        });
        bookingRepository.save(wlBooking);

        SeatInventory inv = seatInventoryRepository.findBySegment(
                cancelledEvent.trainRunId(), cancelledEvent.coachType(),
                cancelledEvent.fromStationId(), cancelledEvent.toStationId()
        ).orElse(null);

        if (inv != null) {
            seatInventoryRepository.decrementWaitlistCount(
                    inv.getId(), wlBooking.getPassengerCount(), inv.getVersion());
        }

        availabilityService.evictCache(wlBooking.getTrainRunId(), wlBooking.getCoachType());
        pnrStatusService.evictCache(wlBooking.getPnr());

        bookingEventPublisher.publishBookingPromoted(wlBooking);
        log.info("Promoted WAITLISTED→RAC: PNR={}", wlBooking.getPnr());
    }

    @DltHandler
    public void handleDlt(EventEnvelope<?> envelope) {
        log.error("Waitlist promotion event sent to DLT: eventType={}, aggregateId={}",
                envelope.getEventType(), envelope.getAggregateId());
    }
}
