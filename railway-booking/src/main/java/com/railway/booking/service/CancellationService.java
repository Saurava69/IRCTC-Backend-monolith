package com.railway.booking.service;

import com.railway.booking.dto.CancellationResponse;
import com.railway.booking.entity.Booking;
import com.railway.booking.entity.BookingStatus;
import com.railway.booking.kafka.BookingEventPublisher;
import com.railway.booking.redis.SeatLockManager;
import com.railway.booking.repository.BookingRepository;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.common.exception.BusinessException;
import com.railway.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancellationService {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatLockManager seatLockManager;
    private final SeatAvailabilityService availabilityService;
    private final PnrStatusService pnrStatusService;
    private final BookingEventPublisher bookingEventPublisher;

    @Transactional
    public CancellationResponse cancelBooking(String pnr, Long requestingUserId,
                                               boolean isAdmin, String reason) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", pnr));

        if (!isAdmin && !booking.getUserId().equals(requestingUserId)) {
            throw new BusinessException("FORBIDDEN", "Not authorized to cancel this booking");
        }

        BookingStatus previousStatus = booking.getBookingStatus();
        if (previousStatus != BookingStatus.CONFIRMED
                && previousStatus != BookingStatus.WAITLISTED
                && previousStatus != BookingStatus.RAC) {
            throw new BusinessException("INVALID_STATUS",
                    "Booking cannot be cancelled in status: " + previousStatus);
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.getPassengers().forEach(p -> p.setStatus(BookingStatus.CANCELLED));
        bookingRepository.save(booking);

        restoreInventory(booking, previousStatus);

        try {
            seatLockManager.releaseLock(booking.getTrainRunId(), booking.getCoachType(),
                    booking.getFromStationId(), booking.getToStationId(),
                    booking.getPassengerCount(), booking.getPnr());
        } catch (Exception e) {
            log.warn("Could not release seat lock for PNR {}: {}", pnr, e.getMessage());
        }

        availabilityService.evictCache(booking.getTrainRunId(), booking.getCoachType());
        pnrStatusService.evictCache(booking.getPnr());

        bookingEventPublisher.publishBookingCancelled(booking, previousStatus.name());

        log.info("Booking CANCELLED: PNR={}, previousStatus={}, reason={}",
                pnr, previousStatus, reason);

        return new CancellationResponse(
                pnr,
                BookingStatus.CANCELLED.name(),
                reason,
                booking.getTotalFare(),
                "REFUND_INITIATED",
                Instant.now()
        );
    }

    private void restoreInventory(Booking booking, BookingStatus previousStatus) {
        seatInventoryRepository.findBySegment(
                booking.getTrainRunId(), booking.getCoachType(),
                booking.getFromStationId(), booking.getToStationId()
        ).ifPresent(inv -> {
            int count = booking.getPassengerCount();
            switch (previousStatus) {
                case CONFIRMED -> seatInventoryRepository.incrementAvailableSeats(
                        inv.getId(), count, inv.getVersion());
                case RAC -> seatInventoryRepository.decrementRacSeats(
                        inv.getId(), count, inv.getVersion());
                case WAITLISTED -> seatInventoryRepository.decrementWaitlistCount(
                        inv.getId(), count, inv.getVersion());
                default -> log.warn("Unexpected previous status for inventory restore: {}", previousStatus);
            }
        });
    }
}
