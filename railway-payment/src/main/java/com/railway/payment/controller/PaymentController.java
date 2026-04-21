package com.railway.payment.controller;

import com.railway.payment.dto.PaymentRequest;
import com.railway.payment.dto.PaymentResponse;
import com.railway.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(
                request.bookingId(), request.paymentMethod(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentByBookingId(bookingId));
    }

    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<PaymentResponse> retryPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.retryPayment(paymentId));
    }
}
