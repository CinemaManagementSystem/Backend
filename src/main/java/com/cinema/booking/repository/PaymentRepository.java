package com.cinema.booking.repository;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime expiresAt);

    Optional<Payment> findByMd5Hash(String md5Hash);

    Optional<Payment> findByTransactionId(String transactionId);
}