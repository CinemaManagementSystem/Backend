package com.cinema.booking.repository;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime expiresAt);

    List<Payment> findByStatusAndPaymentMethodAndMd5HashIsNotNull(PaymentStatus status, PaymentMethod paymentMethod);

    List<Payment> findByCustomerId(Long customerId);

    Optional<Payment> findByMd5Hash(String md5Hash);

    Optional<Payment> findByTransactionId(String transactionId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
