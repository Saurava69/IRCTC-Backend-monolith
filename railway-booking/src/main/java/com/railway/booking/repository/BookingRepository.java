package com.railway.booking.repository;

import com.railway.booking.entity.Booking;
import com.railway.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.passengers WHERE b.pnr = :pnr")
    Optional<Booking> findByPnr(String pnr);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.passengers WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    List<Booking> findAllByUserId(Long userId);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.passengers WHERE b.bookingStatus = :status AND b.createdAt < :cutoff")
    List<Booking> findExpiredBookings(BookingStatus status, Instant cutoff);
}
