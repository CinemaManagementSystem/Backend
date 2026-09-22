package com.cinema.booking.scheduler;

import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.config.BookingHoldConfig;
import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.enums.PaymentVerificationSource;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.service.BookingService;
import com.cinema.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.domain.PageRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpirationScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    private final BookingHoldConfig bookingHoldConfig;
    private final KhqrConfig khqrConfig;
    private final AtomicBoolean paymentPollRunning = new AtomicBoolean(false);

    /**
     * Polls Bakong for pending KHQR payments so the database can move from
     * PENDING to PAID even when the browser stops polling the status endpoint.
     */
    @Scheduled(fixedDelayString = "${bakong.polling-rate-ms:60000}")
    public void pollPendingKhqrPayments() {
        if (!paymentPollRunning.compareAndSet(false, true)) {
            log.warn("Skipping Bakong poll because the previous run is still active");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
            int batchSize = Math.max(1, khqrConfig.getPollingBatchSize());
            List<Payment> pendingKhqrPayments = paymentRepository.findDueKhqrPayments(
                    now, PageRequest.of(0, batchSize));

            log.debug("Bakong pull poll executing: recoverablePayments={}, tokenConfigured={}, mockMode={}",
                    pendingKhqrPayments.size(), hasText(khqrConfig.getToken()), khqrConfig.isMockMode());

            if (pendingKhqrPayments.isEmpty()) {
                return;
            }

            log.debug("Polling Bakong for {} pending/recoverable KHQR payment(s)", pendingKhqrPayments.size());

            for (Payment payment : pendingKhqrPayments) {
                try {
                    PaymentResponseDto response = paymentService.checkStatusFromSystem(
                            payment.getId(), PaymentVerificationSource.SCHEDULED);
                    if (response.status() == PaymentStatus.PAID) {
                        log.info("Pending KHQR payment confirmed by scheduled Bakong polling: paymentId={}, bookingId={}, status={}",
                                payment.getId(), payment.getBooking() != null ? payment.getBooking().getId() : null,
                                response.status());
                    }
                } catch (RuntimeException ex) {
                    log.warn("Failed to poll pending KHQR payment #{}: {}", payment.getId(), ex.getMessage());
                }
            }
        } finally {
            paymentPollRunning.set(false);
        }
    }

    /**
     * Expires abandoned bookings as well as their pending payments and seats.
     * KHQR payments are checked by the polling task before this cascade runs.
     * Legacy bookings without a persisted deadline use the show's start time
     * as a fallback.
     */
    @Scheduled(fixedDelayString = "${booking.expiry-rate-ms:60000}")
    public void expireStaleBookings() {
        int holdMinutes = bookingHoldConfig.getHoldTtlMinutes() > 0
                ? bookingHoldConfig.getHoldTtlMinutes() : 5;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        LocalDateTime showCutoff = now.plusMinutes(holdMinutes);
        bookingRepository.findPendingBookingsPastHoldDeadline(
                        com.cinema.booking.enums.BookingStatus.PENDING, now, showCutoff)
                .forEach(booking -> {
                    try {
                        if (hasInconclusiveBakongVerification(booking.getId(), now)) {
                            log.info("Keeping booking pending while Bakong verification is deferred: bookingId={}",
                                    booking.getId());
                            return;
                        }
                        bookingService.expirePendingBooking(booking.getId());
                    } catch (RuntimeException ex) {
                        log.warn("Failed to expire pending booking #{}: {}", booking.getId(), ex.getMessage());
                    }
                });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasInconclusiveBakongVerification(Long bookingId, LocalDateTime now) {
        int recoveryMinutes = khqrConfig.getRecoveryWindowMinutes() > 0
                ? khqrConfig.getRecoveryWindowMinutes() : 15;
        return paymentRepository.findByBookingId(bookingId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .filter(payment -> payment.getPaymentMethod() == PaymentMethod.KHQR)
                .anyMatch(payment -> (payment.getRateLimitedUntil() != null
                        && now.isBefore(payment.getRateLimitedUntil()))
                        || (payment.getLastVerificationError() != null
                        && payment.getExpiresAt() != null
                        && now.isBefore(payment.getExpiresAt().plusMinutes(recoveryMinutes))));
    }
}
