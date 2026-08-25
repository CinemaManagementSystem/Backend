package com.cinema.booking.scheduler;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Sweeps every 60 seconds to automatically expire pending KHQR payments
     * whose expiration time (expiresAt) has passed.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingPayments() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        List<Payment> expiredPayments = paymentRepository.findByStatusAndExpiresAtBefore(PaymentStatus.PENDING, now);

        if (expiredPayments.isEmpty()) {
            return;
        }

        log.info("Found {} expired pending payment(s) to fail", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setPayment(payment);
            transaction.setBooking(payment.getBooking());
            transaction.setOrder(payment.getOrder());
            transaction.setAmount(payment.getAmount());
            transaction.setTransactionType(payment.getPaymentMethod());
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setReference("EXPIRED-" + (payment.getTransactionId() != null ? payment.getTransactionId() : payment.getId()));
            paymentTransactionRepository.save(transaction);

            log.info("Expired payment #{} (txn: {}) marked as FAILED", payment.getId(), payment.getTransactionId());
        }
    }
}
