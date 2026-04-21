package com.railway.payment.service;

import com.railway.common.exception.BusinessException;
import com.railway.common.exception.ResourceNotFoundException;
import com.railway.payment.dto.PaymentResponse;
import com.railway.payment.entity.Payment;
import com.railway.payment.entity.PaymentStatus;
import com.railway.payment.gateway.MockPaymentGateway;
import com.railway.payment.kafka.PaymentEventPublisher;
import com.railway.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public PaymentResponse initiatePayment(Long bookingId, String paymentMethod, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent payment request for key {}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        Object[] booking = lookupBooking(bookingId);
        String pnr = (String) booking[0];
        BigDecimal amount = (BigDecimal) booking[1];
        String status = (String) booking[2];

        if (!"PAYMENT_PENDING".equals(status)) {
            throw new BusinessException("INVALID_BOOKING_STATUS",
                    "Booking is not in PAYMENT_PENDING status. Current: " + status);
        }

        List<Payment> existingPayments = paymentRepository.findByBookingId(bookingId);
        boolean hasSuccessful = existingPayments.stream()
                .anyMatch(p -> p.getPaymentStatus() == PaymentStatus.SUCCESS);
        if (hasSuccessful) {
            throw new BusinessException("ALREADY_PAID", "Booking already has a successful payment");
        }

        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .pnr(pnr)
                .amount(amount)
                .paymentStatus(PaymentStatus.PROCESSING)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey)
                .build();
        payment = paymentRepository.save(payment);

        MockPaymentGateway.GatewayResponse gatewayResponse =
                paymentGateway.processPayment(bookingId, amount, paymentMethod);

        payment.setGatewayTransactionId(gatewayResponse.transactionId());
        payment.setGatewayResponse(gatewayResponse.message());

        if (gatewayResponse.success()) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment = paymentRepository.save(payment);
            eventPublisher.publishPaymentSuccess(payment);
            log.info("Payment SUCCESS for booking {}, payment {}", bookingId, payment.getId());
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.failureReason());
            payment = paymentRepository.save(payment);
            eventPublisher.publishPaymentFailed(payment);
            log.info("Payment FAILED for booking {}: {}", bookingId, gatewayResponse.failureReason());
        }

        return toResponse(payment);
    }

    public PaymentResponse getPaymentByBookingId(Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        if (payments.isEmpty()) {
            throw new ResourceNotFoundException("Payment", bookingId);
        }
        return toResponse(payments.get(payments.size() - 1));
    }

    @Transactional
    public PaymentResponse retryPayment(Long paymentId) {
        Payment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (original.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new BusinessException("INVALID_PAYMENT_STATUS",
                    "Can only retry failed payments. Current: " + original.getPaymentStatus());
        }

        return initiatePayment(original.getBookingId(), original.getPaymentMethod(), null);
    }

    @SuppressWarnings("unchecked")
    private Object[] lookupBooking(Long bookingId) {
        List<Object[]> results = entityManager.createNativeQuery(
                "SELECT b.pnr, b.total_fare, b.booking_status FROM bookings b WHERE b.id = :id"
        ).setParameter("id", bookingId).getResultList();

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return results.get(0);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getPnr(),
                payment.getAmount(),
                payment.getPaymentStatus().name(),
                payment.getPaymentMethod(),
                payment.getGatewayTransactionId(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }
}
