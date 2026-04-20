package com.railway.booking.service;

import com.railway.booking.dto.SeatAvailabilityResponse;
import com.railway.booking.entity.SeatInventory;
import com.railway.booking.redis.AvailabilityCache;
import com.railway.booking.redis.SeatLockManager;
import com.railway.booking.repository.SeatInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatAvailabilityService {

    private final SeatInventoryRepository seatInventoryRepository;
    private final AvailabilityCache availabilityCache;
    private final SeatLockManager seatLockManager;

    public SeatAvailabilityResponse getAvailability(Long trainRunId, String coachType) {
        Optional<SeatAvailabilityResponse> cached = availabilityCache.get(trainRunId, coachType);
        if (cached.isPresent()) {
            log.debug("Cache HIT for availability trainRun={} coachType={}", trainRunId, coachType);
            return cached.get();
        }

        log.debug("Cache MISS for availability trainRun={} coachType={}", trainRunId, coachType);
        List<SeatInventory> segments = seatInventoryRepository
                .findByTrainRunAndCoachType(trainRunId, coachType);

        int totalSeats = 0;
        int availableSeats = 0;
        int racAvailable = 0;
        int waitlistCount = 0;

        for (SeatInventory segment : segments) {
            totalSeats = Math.max(totalSeats, segment.getTotalSeats());
            availableSeats = Math.min(availableSeats == 0 ? Integer.MAX_VALUE : availableSeats,
                    segment.getAvailableSeats());
            racAvailable += segment.getRacSeats();
            waitlistCount = Math.max(waitlistCount, segment.getWaitlistCount());
        }

        if (segments.isEmpty()) {
            availableSeats = 0;
        }

        SeatAvailabilityResponse response = new SeatAvailabilityResponse(
                trainRunId, coachType, totalSeats, availableSeats, racAvailable, waitlistCount);

        availabilityCache.put(trainRunId, coachType, response);
        return response;
    }

    public SeatAvailabilityResponse getSegmentAvailability(Long trainRunId, String coachType,
                                                           Long fromStationId, Long toStationId) {
        Optional<SeatInventory> segment = seatInventoryRepository
                .findBySegment(trainRunId, coachType, fromStationId, toStationId);

        if (segment.isEmpty()) {
            return new SeatAvailabilityResponse(trainRunId, coachType, 0, 0, 0, 0);
        }

        SeatInventory si = segment.get();
        seatLockManager.setAvailabilityCount(trainRunId, coachType,
                fromStationId, toStationId, si.getAvailableSeats());

        return new SeatAvailabilityResponse(
                trainRunId, coachType, si.getTotalSeats(),
                si.getAvailableSeats(), si.getRacSeats(), si.getWaitlistCount());
    }

    public void evictCache(Long trainRunId, String coachType) {
        availabilityCache.evict(trainRunId, coachType);
    }
}
