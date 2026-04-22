package com.railway.booking.service;

import com.railway.booking.dto.BookingRequest;
import com.railway.booking.dto.BookingResponse;
import com.railway.booking.entity.*;
import com.railway.booking.kafka.BookingEventPublisher;
import com.railway.booking.redis.IdempotencyStore;
import com.railway.booking.redis.SeatLockManager;
import com.railway.booking.repository.BookingRepository;
import com.railway.booking.repository.SeatInventoryRepository;
import com.railway.common.exception.BusinessException;
import com.railway.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatLockManager seatLockManager;
    private final IdempotencyStore idempotencyStore;
    private final PnrGenerator pnrGenerator;
    private final SeatAvailabilityService availabilityService;
    private final PnrStatusService pnrStatusService;
    private final TrainRunService trainRunService;
    private final BookingEventPublisher bookingEventPublisher;

    @Value("${app.booking.max-passengers-per-booking:6}")
    private int maxPassengers;

    @Transactional
    public BookingResponse initiateBooking(BookingRequest request, Long userId) {
        if (request.idempotencyKey() != null) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent request detected for key {}", request.idempotencyKey());
                return toResponse(existing.get());
            }

            if (idempotencyStore.isProcessing(request.idempotencyKey())) {
                throw new BusinessException("DUPLICATE_REQUEST", "Request is already being processed");
            }
            idempotencyStore.tryAcquire(request.idempotencyKey());
        }

        try {
            validateBookingRequest(request);

            TrainRun trainRun = trainRunService.getTrainRun(request.trainRunId());
            if (!"SCHEDULED".equals(trainRun.getStatus())) {
                throw new BusinessException("TRAIN_NOT_AVAILABLE", "Train run is not available for booking");
            }

            int passengerCount = request.passengers().size();
            SeatInventory inventory = seatInventoryRepository
                    .findBySegment(request.trainRunId(), request.coachType(),
                            request.fromStationId(), request.toStationId())
                    .orElseThrow(() -> new BusinessException("NO_INVENTORY",
                            "No seat inventory found for the requested segment"));

            BookingStatus status;
            if (inventory.getAvailableSeats() >= passengerCount) {
                status = BookingStatus.PAYMENT_PENDING;
                String bookingId = pnrGenerator.generate();
                seatLockManager.lockSeats(request.trainRunId(), request.coachType(),
                        request.fromStationId(), request.toStationId(),
                        passengerCount, bookingId);

                int updated = seatInventoryRepository.decrementAvailableSeats(
                        inventory.getId(), passengerCount, inventory.getVersion());
                if (updated == 0) {
                    seatLockManager.releaseLock(request.trainRunId(), request.coachType(),
                            request.fromStationId(), request.toStationId(),
                            passengerCount, bookingId);
                    throw new BusinessException("CONCURRENT_BOOKING",
                            "Seats were booked by another user. Please try again.");
                }
            } else {
                int racCapacity = (int) Math.ceil(inventory.getTotalSeats() * 0.10);
                if (inventory.getRacSeats() + passengerCount <= racCapacity) {
                    status = BookingStatus.RAC;
                    int updated = seatInventoryRepository.incrementRacSeats(
                            inventory.getId(), passengerCount, inventory.getVersion());
                    if (updated == 0) {
                        throw new BusinessException("CONCURRENT_BOOKING",
                                "RAC slots taken, please retry");
                    }
                } else {
                    status = BookingStatus.WAITLISTED;
                    int updated = seatInventoryRepository.incrementWaitlistCount(
                            inventory.getId(), passengerCount, inventory.getVersion());
                    if (updated == 0) {
                        throw new BusinessException("CONCURRENT_BOOKING",
                                "Waitlist update conflict, please retry");
                    }
                }
            }

            String pnr = pnrGenerator.generate();
            BigDecimal fare = calculateFare(request.coachType(), passengerCount);

            Booking booking = Booking.builder()
                    .pnr(pnr)
                    .userId(userId)
                    .trainRunId(request.trainRunId())
                    .coachType(request.coachType())
                    .fromStationId(request.fromStationId())
                    .toStationId(request.toStationId())
                    .bookingStatus(status)
                    .totalFare(fare)
                    .passengerCount(passengerCount)
                    .idempotencyKey(request.idempotencyKey())
                    .build();

            for (int i = 0; i < request.passengers().size(); i++) {
                BookingRequest.PassengerRequest pr = request.passengers().get(i);
                BookingPassenger.BookingPassengerBuilder passengerBuilder = BookingPassenger.builder()
                        .booking(booking)
                        .name(pr.name())
                        .age(pr.age())
                        .gender(pr.gender())
                        .berthPreference(pr.berthPreference())
                        .status(status);

                if (status == BookingStatus.RAC) {
                    passengerBuilder.racNumber(inventory.getRacSeats() + i + 1);
                } else if (status == BookingStatus.WAITLISTED) {
                    passengerBuilder.waitlistNumber(inventory.getWaitlistCount() + i + 1);
                }

                booking.getPassengers().add(passengerBuilder.build());
            }

            booking = bookingRepository.save(booking);

            availabilityService.evictCache(request.trainRunId(), request.coachType());

            bookingEventPublisher.publishBookingInitiated(booking);

            if (request.idempotencyKey() != null) {
                idempotencyStore.markCompleted(request.idempotencyKey(), booking.getPnr());
            }

            log.info("Booking initiated: PNR={}, status={}, passengers={}",
                    pnr, status, passengerCount);

            return toResponse(booking);

        } catch (Exception e) {
            if (request.idempotencyKey() != null) {
                idempotencyStore.remove(request.idempotencyKey());
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByPnr(String pnr) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", pnr));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    private void validateBookingRequest(BookingRequest request) {
        if (request.passengers().size() > maxPassengers) {
            throw new BusinessException("TOO_MANY_PASSENGERS",
                    "Maximum " + maxPassengers + " passengers per booking");
        }
        if (request.fromStationId().equals(request.toStationId())) {
            throw new BusinessException("INVALID_STATIONS",
                    "Source and destination stations must be different");
        }
    }

    private BigDecimal calculateFare(String coachType, int passengerCount) {
        BigDecimal baseRate = switch (coachType) {
            case "FIRST_AC" -> new BigDecimal("2500");
            case "SECOND_AC" -> new BigDecimal("1500");
            case "THIRD_AC" -> new BigDecimal("1000");
            case "SLEEPER" -> new BigDecimal("500");
            default -> new BigDecimal("200");
        };
        return baseRate.multiply(BigDecimal.valueOf(passengerCount));
    }

    private BookingResponse toResponse(Booking booking) {
        List<BookingResponse.PassengerResponse> passengers = booking.getPassengers().stream()
                .map(p -> new BookingResponse.PassengerResponse(
                        p.getId(), p.getName(), p.getAge(), p.getGender(),
                        p.getBerthPreference(), p.getSeatNumber(), p.getCoachNumber(),
                        p.getStatus().name(), p.getWaitlistNumber(), p.getRacNumber()))
                .toList();

        return new BookingResponse(
                booking.getId(), booking.getPnr(), booking.getBookingStatus().name(),
                booking.getTrainRunId(), booking.getCoachType(),
                booking.getFromStationId(), booking.getToStationId(),
                booking.getTotalFare(), booking.getPassengerCount(),
                passengers, booking.getCreatedAt());
    }
}
