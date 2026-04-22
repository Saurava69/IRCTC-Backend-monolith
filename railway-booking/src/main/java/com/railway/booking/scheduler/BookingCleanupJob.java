package com.railway.booking.scheduler;

import com.railway.booking.entity.Booking;
import com.railway.booking.entity.BookingStatus;
import com.railway.booking.kafka.BookingEventPublisher;
import com.railway.booking.repository.BookingRepository;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.booking.service.PnrStatusService;
import com.railway.booking.service.SeatAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupJob {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatAvailabilityService availabilityService;
    private final PnrStatusService pnrStatusService;
    private final BookingEventPublisher bookingEventPublisher;

    @Value("${app.booking.payment-timeout-seconds:600}")
    private long paymentTimeoutSeconds;

    @Scheduled(fixedDelayString = "${app.scheduler.booking-cleanup-interval-ms:60000}")
    @Transactional
    public void cleanupExpiredBookings() {
        Instant cutoff = Instant.now().minusSeconds(paymentTimeoutSeconds);
        List<Booking> expired = bookingRepository
                .findExpiredBookings(BookingStatus.PAYMENT_PENDING, cutoff);

        for (Booking booking : expired) {
            booking.setBookingStatus(BookingStatus.FAILED);
            bookingRepository.save(booking);

            seatInventoryRepository.findBySegment(
                    booking.getTrainRunId(), booking.getCoachType(),
                    booking.getFromStationId(), booking.getToStationId()
            ).ifPresent(inv -> seatInventoryRepository.incrementAvailableSeats(
                    inv.getId(), booking.getPassengerCount(), inv.getVersion()));

            availabilityService.evictCache(booking.getTrainRunId(), booking.getCoachType());
            pnrStatusService.evictCache(booking.getPnr());
            bookingEventPublisher.publishBookingFailed(booking);

            log.info("Expired booking: PNR={}", booking.getPnr());
        }

        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired bookings", expired.size());
        }
    }
}
