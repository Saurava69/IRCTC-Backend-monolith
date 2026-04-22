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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "7. Bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;

    @PostMapping
    @RateLimit(requests = 5, windowSeconds = 60, keyPrefix = "booking")
    @Operation(summary = "Initiate a booking",
            description = """
                    Book seats on a train run. Requires prior seat availability check.
                    Seats are locked in Redis for 10 minutes while payment is pending.
                    Status will be PAYMENT_PENDING (confirmed seats), RAC, or WAITLISTED depending on availability.
                    Rate limited: 5 requests per minute per user.""")
    @ApiResponse(responseCode = "201", description = "Booking created — proceed to payment")
    @ApiResponse(responseCode = "400", description = "Invalid request or no availability")
    @ApiResponse(responseCode = "409", description = "Seats already locked by another user")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<BookingResponse> initiateBooking(
            @Valid @RequestBody BookingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        BookingResponse response = bookingService.initiateBooking(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pnr}")
    @Operation(summary = "Get booking by PNR",
            description = "Lookup a booking by its PNR number. Returns full booking details with passenger list.")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "404", description = "PNR not found")
    public ResponseEntity<BookingResponse> getByPnr(
            @Parameter(description = "PNR number", example = "PNR2026042200001")
            @PathVariable String pnr) {
        return ResponseEntity.ok(bookingService.getBookingByPnr(pnr));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my bookings",
            description = "Returns paginated list of bookings for the authenticated user, ordered by most recent first.")
    @ApiResponse(responseCode = "200", description = "Bookings list returned")
    public ResponseEntity<PagedResponse<BookingResponse>> getMyBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal User user,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(bookingService.getUserBookings(user.getId(), pageable)));
    }

    @PostMapping("/{pnr}/cancel")
    @Operation(summary = "Cancel a booking",
            description = """
                    Cancel a CONFIRMED, RAC, or WAITLISTED booking. Triggers an event chain:
                    1. Booking status → CANCELLED, inventory restored
                    2. Kafka event → payment module initiates refund
                    3. Kafka event → waitlist promotion (RAC→CONFIRMED, WAITLISTED→RAC)
                    4. Kafka event → notification sent
                    Users can cancel their own bookings; admins can cancel any booking.""")
    @ApiResponse(responseCode = "200", description = "Booking cancelled — refund initiated")
    @ApiResponse(responseCode = "400", description = "Booking cannot be cancelled (already cancelled or expired)")
    @ApiResponse(responseCode = "403", description = "Not authorized to cancel this booking")
    @ApiResponse(responseCode = "404", description = "PNR not found")
    public ResponseEntity<CancellationResponse> cancelBooking(
            @Parameter(description = "PNR number", example = "PNR2026042200001")
            @PathVariable String pnr,
            @RequestBody(required = false) CancellationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        String reason = request != null ? request.cancellationReason() : null;
        return ResponseEntity.ok(
                cancellationService.cancelBooking(pnr, user.getId(), isAdmin, reason));
    }
}
