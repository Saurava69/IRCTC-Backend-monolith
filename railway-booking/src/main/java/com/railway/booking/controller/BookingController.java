package com.railway.booking.controller;

import com.railway.booking.dto.BookingRequest;
import com.railway.booking.dto.BookingResponse;
import com.railway.booking.dto.CancellationRequest;
import com.railway.booking.dto.CancellationResponse;
import com.railway.booking.ratelimit.RateLimit;
import com.railway.booking.service.BookingService;
import com.railway.booking.service.CancellationService;
import com.railway.common.dto.PagedResponse;
import com.railway.user.entity.Role;
import com.railway.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;

    @PostMapping
    @RateLimit(requests = 5, windowSeconds = 60, keyPrefix = "booking")
    public ResponseEntity<BookingResponse> initiateBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal User user) {
        BookingResponse response = bookingService.initiateBooking(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pnr}")
    public ResponseEntity<BookingResponse> getByPnr(@PathVariable String pnr) {
        return ResponseEntity.ok(bookingService.getBookingByPnr(pnr));
    }

    @GetMapping("/my")
    public ResponseEntity<PagedResponse<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(bookingService.getUserBookings(user.getId(), pageable)));
    }

    @PostMapping("/{pnr}/cancel")
    public ResponseEntity<CancellationResponse> cancelBooking(
            @PathVariable String pnr,
            @RequestBody(required = false) CancellationRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        String reason = request != null ? request.cancellationReason() : null;
        return ResponseEntity.ok(
                cancellationService.cancelBooking(pnr, user.getId(), isAdmin, reason));
    }
}
