package com.cinema.booking.repository;

import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByPaymentId(Long paymentId);

    List<PaymentTransaction> findByPaymentIdAndReferenceOrderByIdAsc(Long paymentId, String reference);

    List<PaymentTransaction> findByPaymentIdAndStatusOrderByIdAsc(
            Long paymentId,
            PaymentStatus status
    );

    List<PaymentTransaction> findByPaymentCustomerId(Long customerId);
}
