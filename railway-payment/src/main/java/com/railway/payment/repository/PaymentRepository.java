package com.railway.payment.repository;

import com.railway.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByPnr(String pnr);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
