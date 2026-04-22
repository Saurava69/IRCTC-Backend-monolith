package com.railway.payment.controller;

import com.railway.payment.dto.PaymentRequest;
import com.railway.payment.dto.PaymentResponse;
import com.railway.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "9. Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate payment for a booking",
            description = """
                    Process payment for a PAYMENT_PENDING, RAC, or WAITLISTED booking.
                    Uses a mock payment gateway (95% success rate, 50-200ms latency).
                    Idempotent — duplicate requests with the same idempotencyKey return the existing payment.
                    On success, a Kafka event confirms the booking.""")
    @ApiResponse(responseCode = "201", description = "Payment processed successfully")
    @ApiResponse(responseCode = "400", description = "Booking not in payable status")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @ApiResponse(responseCode = "409", description = "Payment already exists for this booking")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(
                request.bookingId(), request.paymentMethod(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking ID",
            description = "Lookup the payment record associated with a booking.")
    @ApiResponse(responseCode = "200", description = "Payment found")
    @ApiResponse(responseCode = "404", description = "No payment found for this booking")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(
            @Parameter(description = "Booking ID", example = "1")
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentByBookingId(bookingId));
    }

    @PostMapping("/{paymentId}/retry")
    @Operation(summary = "Retry a failed payment",
            description = "Retry a payment that previously failed. Only works for payments in FAILED status.")
    @ApiResponse(responseCode = "200", description = "Payment retried successfully")
    @ApiResponse(responseCode = "400", description = "Payment is not in FAILED status")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    public ResponseEntity<PaymentResponse> retryPayment(
            @Parameter(description = "Payment ID", example = "1")
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.retryPayment(paymentId));
    }
}
