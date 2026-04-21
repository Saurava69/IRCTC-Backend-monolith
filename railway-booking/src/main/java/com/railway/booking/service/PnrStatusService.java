package com.railway.booking.service;

import com.railway.booking.dto.PnrStatusResponse;
import com.railway.booking.entity.Booking;
import com.railway.booking.redis.PnrCache;
import com.railway.booking.repository.BookingRepository;
import com.railway.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnrStatusService {

    private final BookingRepository bookingRepository;
    private final PnrCache pnrCache;

    @Transactional(readOnly = true)
    public PnrStatusResponse getStatus(String pnr) {
        Optional<PnrStatusResponse> cached = pnrCache.get(pnr);
        if (cached.isPresent()) {
            log.debug("PNR cache HIT for {}", pnr);
            return cached.get();
        }

        log.debug("PNR cache MISS for {}", pnr);
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", pnr));

        PnrStatusResponse response = buildPnrResponse(booking);
        pnrCache.put(pnr, response);
        return response;
    }

    public void evictCache(String pnr) {
        pnrCache.evict(pnr);
    }

    private PnrStatusResponse buildPnrResponse(Booking booking) {
        var passengers = booking.getPassengers().stream()
                .map(p -> new PnrStatusResponse.PassengerStatus(
                        p.getName(), p.getAge(), p.getStatus().name(),
                        p.getSeatNumber(), p.getCoachNumber(),
                        p.getWaitlistNumber(), p.getRacNumber()))
                .toList();

        return new PnrStatusResponse(
                booking.getPnr(),
                booking.getBookingStatus().name(),
                booking.getTrainRunId(),
                booking.getCoachType(),
                booking.getFromStationId(),
                booking.getToStationId(),
                passengers);
    }
}
