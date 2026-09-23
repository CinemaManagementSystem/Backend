package com.cinema.booking.repository;

import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByPaymentId(Long paymentId);

    List<PaymentTransaction> findByPaymentIdAndReferenceOrderByIdAsc(Long paymentId, String reference);

    List<PaymentTransaction> findByPaymentIdAndStatusOrderByIdAsc(
            Long paymentId,
            PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.payment.id = :paymentId and pt.status = :status order by pt.id")
    List<PaymentTransaction> findByPaymentIdAndStatusForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("status") PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.payment.id = :paymentId and pt.reference = :reference order by pt.id")
    List<PaymentTransaction> findByPaymentIdAndReferenceForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("reference") String reference
    );

    List<PaymentTransaction> findByPaymentCustomerId(Long customerId);

    Page<PaymentTransaction> findByPaymentCustomerId(Long customerId, Pageable pageable);

    Page<PaymentTransaction> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentTransaction> findByPaymentCustomerIdAndStatus(Long customerId, PaymentStatus status, Pageable pageable);
}
