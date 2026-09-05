package com.cinema.booking.scheduler;

import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.service.BakongService;
import com.cinema.booking.service.PaymentService;
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
    private final BakongService bakongService;
    private final PaymentService paymentService;

    /**
     * Polls Bakong for pending KHQR payments so the database can move from
     * PENDING to PAID even when the browser stops polling the status endpoint.
     */
    @Scheduled(fixedRateString = "${bakong.polling-rate-ms:10000}")
    public void pollPendingKhqrPayments() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        List<Payment> pendingKhqrPayments = paymentRepository
                .findByStatusAndPaymentMethodAndMd5HashIsNotNull(PaymentStatus.PENDING, PaymentMethod.KHQR);

        if (pendingKhqrPayments.isEmpty()) {
            return;
        }

        log.debug("Polling Bakong for {} pending KHQR payment(s)", pendingKhqrPayments.size());

        for (Payment payment : pendingKhqrPayments) {
            if (payment.getExpiresAt() != null && now.isAfter(payment.getExpiresAt())) {
                continue;
            }

            BakongCheckResult result = bakongService.checkTransactionByMd5(payment.getMd5Hash());
            if (result.paid()) {
                log.info("Pending KHQR payment #{} confirmed by scheduled Bakong polling", payment.getId());
                paymentService.confirmPayment(payment.getId());
            }
        }
    }

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
