package com.cinema.booking.service.impl;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.MembershipStatus;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.enums.PaymentVerificationSource;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.PaymentMapper;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.repository.UserMembershipRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BakongService;
import com.cinema.booking.service.BakongRequestBudgetService;
import com.cinema.booking.service.BookingTotalService;
import com.cinema.booking.service.BookingService;
import com.cinema.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final ZoneId PHNOM_PENH_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final int MAX_MANUAL_CHECKS = 2;
    private static final int[] SCHEDULED_VERIFICATION_SECONDS = {60, 120, 180, 240};

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final OrderRepository orderRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final BakongService bakongService;
    private final BakongRequestBudgetService bakongRequestBudgetService;
    private final BookingTotalService bookingTotalService;
    private final BookingService bookingService;
    private final KhqrConfig khqrConfig;
    private final AuthorizationService authorizationService;
    private final ConcurrentHashMap<Long, ReentrantLock> verificationLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public PaymentResponseDto create(PaymentRequestDto dto) {
        User customer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.customerId());

        Booking booking = null;
        if (dto.bookingId() != null) {
            booking = bookingRepository.findByIdForUpdate(dto.bookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.bookingId()));
            authorizationService.requireOwnerOrStaff(booking.getCustomer());
            ensureSameCustomer(customer, booking.getCustomer(), "Booking");
            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new IllegalStateException("Only pending bookings can be paid");
            }
        }

        Order order = null;
        if (dto.orderId() != null) {
            order = orderRepository.findById(dto.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.orderId()));
            authorizationService.requireOwnerOrStaff(order.getCustomer());
            ensureSameCustomer(customer, order.getCustomer(), "Order");
            if (booking != null && (order.getBooking() == null
                    || !booking.getId().equals(order.getBooking().getId()))) {
                throw new IllegalArgumentException("Order must belong to the same booking as the payment");
            }
            if (booking == null && order.getBooking() != null) {
                throw new IllegalArgumentException("A booking-linked order must be paid with its booking");
            }
        }

        UserMembership userMembership = null;
        if (dto.userMembershipId() != null) {
            if (booking != null || order != null) {
                throw new IllegalArgumentException("Membership payments cannot be mixed with booking or order payments");
            }
            userMembership = userMembershipRepository.findByIdForUpdate(dto.userMembershipId())
                    .orElseThrow(() -> new ResourceNotFoundException("UserMembership", dto.userMembershipId()));
            authorizationService.requireOwnerOrStaff(userMembership.getCustomer());
            ensureSameCustomer(customer, userMembership.getCustomer(), "Membership");
            if (userMembership.getStatus() != MembershipStatus.PENDING_PAYMENT) {
                throw new IllegalStateException("Only pending memberships can be paid");
            }
        }

        PaymentMethod method = dto.paymentMethod();
        BigDecimal expectedAmount = BigDecimal.ZERO;
        if (booking != null) {
            expectedAmount = bookingTotalService.recalculate(booking);
            if (expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Booking total must be greater than zero");
            }
            ensureBookingHoldActive(booking);
        }
        if (order != null) {
            expectedAmount = expectedAmount.add(bookingTotalService.recalculateOrder(order));
        }
        if (userMembership != null) {
            expectedAmount = userMembership.getPriceSnapshot();
            if (method != PaymentMethod.KHQR) {
                throw new IllegalArgumentException("Membership subscriptions must be paid by KHQR");
            }
        }
        if (booking != null || order != null) {
            if (dto.amount().compareTo(expectedAmount) != 0) {
                throw new IllegalArgumentException("Payment amount must match the current booking/order total: " + expectedAmount);
            }
        }
        if (userMembership != null && dto.amount().compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("Payment amount must match the membership plan price: " + expectedAmount);
        }

        BigDecimal amount = dto.amount();
        Payment payment = null;
        if (booking != null) {
            List<Payment> pendingPayments = paymentRepository.findByBookingIdAndStatusOrderByIdAsc(
                    booking.getId(), PaymentStatus.PENDING);
            if (!pendingPayments.isEmpty()) {
                payment = pendingPayments.get(0);
                if (payment.getPaymentMethod() != null && payment.getPaymentMethod() != method) {
                    throw new IllegalArgumentException("Payment method cannot change while retrying a pending payment");
                }
                markPendingAttemptFailed(payment);
                for (Payment duplicate : pendingPayments.subList(1, pendingPayments.size())) {
                    duplicate.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(duplicate);
                    markPendingAttemptFailed(duplicate);
                }
            }
        }
        if (payment == null && userMembership != null) {
            List<Payment> pendingPayments = paymentRepository.findByUserMembershipIdAndStatusOrderByIdAsc(
                    userMembership.getId(), PaymentStatus.PENDING);
            if (!pendingPayments.isEmpty()) {
                payment = pendingPayments.get(0);
                markPendingAttemptFailed(payment);
                for (Payment duplicate : pendingPayments.subList(1, pendingPayments.size())) {
                    duplicate.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(duplicate);
                    markPendingAttemptFailed(duplicate);
                }
            }
        }
        if (payment != null && payment.getOrder() != null && order == null) {
            throw new IllegalArgumentException("Existing pending payment includes an order; include orderId to resume it");
        }
        if (payment == null) {
            payment = paymentMapper.toEntity(dto);
        }

        payment.setCustomer(customer);
        payment.setBooking(booking);
        payment.setOrder(order);
        payment.setUserMembership(userMembership);
        payment.setAmount(amount);
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PENDING);

        String txId = "TXN-" + method.name() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        if (method == PaymentMethod.KHQR) {
            // Generate standard EMVCo/NBC KHQR payload and MD5 hash
            payment.setTransactionId(txId);
            String currency = khqrConfig.getCurrency() != null ? khqrConfig.getCurrency() : "USD";
            KhqrPayload khqr = booking != null && booking.getExpiresAt() != null
                    ? bakongService.generateDynamicKhqr(
                            amount, currency, txId, "Cinema Booking",
                            null, null, booking.getExpiresAt())
                    : userMembership != null
                    ? bakongService.generateDynamicKhqr(
                            amount, currency, txId, "Cinema Membership",
                            null, null)
                    : bakongService.generateDynamicKhqr(
                            amount, currency, txId, "Cinema Booking",
                            null, null);
            payment.setKhqrString(khqr.khqrString());
            payment.setMd5Hash(khqr.md5Hash());
            payment.setExpiresAt(khqr.expiresAt());
            initializeVerificationState(payment, LocalDateTime.now(PHNOM_PENH_ZONE));
        } else {
            // CASH payment at cinema counter — no gateway reference, nothing to expire
            payment.setTransactionId(null);
            payment.setKhqrString(null);
            payment.setMd5Hash(null);
            payment.setExpiresAt(null);
            clearVerificationState(payment);
        }

        payment = paymentRepository.save(payment);

        // Record initial pending payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setBooking(booking);
        transaction.setOrder(order);
        transaction.setAmount(amount);
        transaction.setTransactionType(method);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setReference(txId);
        paymentTransactionRepository.save(transaction);

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto confirmPayment(Long id) {
        authorizationService.requireStaffOrAdmin();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        if (payment.getPaymentMethod() != PaymentMethod.CASH) {
            throw new IllegalStateException("KHQR payments must be confirmed by Bakong verification");
        }
        if (payment.getBooking() != null && paymentRepository.findByBookingId(payment.getBooking().getId()).stream()
                .anyMatch(attempt -> attempt.getPaymentMethod() == PaymentMethod.KHQR
                        && attempt.getExpiresAt() != null
                        && attempt.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"))))) {
            throw new IllegalStateException("Wait for the previous KHQR to expire before collecting cash");
        }
        return confirmPaymentInternal(id);
    }

    @Override
    @Transactional
    public PaymentResponseDto confirmPaymentFromSystem(Long id) {
        return confirmPaymentInternal(id);
    }

    private PaymentResponseDto confirmPaymentInternal(Long id) {
        return confirmPaymentInternal(id, false);
    }

    /**
     * The only database operation that turns a verified payment into a
     * confirmed booking. A late Bakong result may recover a FAILED payment,
     * but only after the exact payment MD5 was verified and the seats are
     * locked and still available.
     */
    private PaymentResponseDto confirmPaymentInternal(Long id, boolean allowLateRecovery) {
        Payment snapshot = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        Booking lockedBooking = snapshot.getBooking() == null ? null
                : bookingRepository.findByIdForUpdate(snapshot.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", snapshot.getBooking().getId()));
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        if (payment.getStatus() == PaymentStatus.PAID) {
            activateMembershipIfNeeded(payment);
            return paymentMapper.toResponseDto(payment);
        }

        boolean lateRecovery = allowLateRecovery
                && (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.EXPIRED);
        if (payment.getStatus() != PaymentStatus.PENDING && !lateRecovery) {
            return paymentMapper.toResponseDto(payment);
        }

        Booking booking = lockedBooking != null ? lockedBooking : payment.getBooking();
        List<BookingSeat> bookingSeats = List.of();
        if (booking != null) {
            boolean alreadyPaid = paymentRepository.findByBookingId(booking.getId()).stream()
                    .anyMatch(other -> !other.getId().equals(id) && other.getStatus() == PaymentStatus.PAID);
            if (alreadyPaid) throw new IllegalStateException("This booking already has a paid payment");
            if (booking.getStatus() == BookingStatus.EXPIRED && !lateRecovery) {
                throw new IllegalStateException("An expired booking requires late Bakong recovery");
            }
            if (booking.getStatus() == BookingStatus.CANCELLED
                    || (booking.getStatus() != BookingStatus.PENDING
                    && booking.getStatus() != BookingStatus.EXPIRED
                    && booking.getStatus() != BookingStatus.CONFIRMED)) {
                throw new IllegalStateException("A payment cannot confirm this booking");
            }
            if (lateRecovery && booking.getStatus() == BookingStatus.EXPIRED) {
                log.warn("Attempting late recovery for expired booking #{}", booking.getId());
            }
            bookingSeats = bookingSeatRepository.findByBookingIdForUpdate(booking.getId());
            ensureSeatsCanBeConfirmed(booking, bookingSeats);
        }

        PaymentStatus previousPaymentStatus = payment.getStatus();
        BookingStatus previousBookingStatus = booking != null ? booking.getStatus() : null;

        payment.setStatus(PaymentStatus.PAID);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")));
        }

        if (booking != null) {
            payment.setBooking(booking);
            booking.transitionTo(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            bookingSeats.forEach(bookingSeat -> bookingSeat.setStatus("CONFIRMED"));
            bookingSeatRepository.saveAll(bookingSeats);
            for (Payment other : paymentRepository.findByBookingIdAndStatusOrderByIdAsc(booking.getId(), PaymentStatus.PENDING)) {
                if (!other.getId().equals(id)) {
                    other.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(other);
                    markPendingTransactions(other, PaymentStatus.FAILED);
                }
            }
        }

        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            order.setStatus("CONFIRMED");
            orderRepository.save(order);
        }

        activateMembershipIfNeeded(payment);

        payment = paymentRepository.save(payment);

        PaymentTransaction transaction = findOrCreateTransaction(payment);
        transaction.setStatus(PaymentStatus.PAID);
        paymentTransactionRepository.save(transaction);

        log.info("Payment confirmation persisted: paymentId={}, bookingId={}, transactionId={}, md5={}, paymentStatus={}, bookingStatus={}, seatCount={}",
                payment.getId(), booking != null ? booking.getId() : null, payment.getTransactionId(),
                maskHash(payment.getMd5Hash()), payment.getStatus(),
                booking != null ? booking.getStatus() : null, bookingSeats.size());
        log.info("Payment status transition after Bakong verification: paymentId={}, paymentStatus={}->{}, bookingStatus={}->{}, seatCount={}",
                payment.getId(), previousPaymentStatus, payment.getStatus(),
                previousBookingStatus, booking != null ? booking.getStatus() : null, bookingSeats.size());

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatus(Long id) {
        return checkStatus(id, PaymentVerificationSource.MANUAL);
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatus(Long id, PaymentVerificationSource source) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(payment.getCustomer());
        return withVerificationLock(id, () -> checkStatusInternal(id, source));
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatusFromSystem(Long id) {
        return checkStatusFromSystem(id, PaymentVerificationSource.SYSTEM);
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatusFromSystem(Long id, PaymentVerificationSource source) {
        return withVerificationLock(id, () -> checkStatusInternal(id, source));
    }

    @Override
    @Transactional
    public PaymentResponseDto switchToCash(Long id) {
        Payment snapshot = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(snapshot.getCustomer());
        // Use the same booking -> payment lock order as payment confirmation.
        if (snapshot.getBooking() == null) {
            throw new IllegalArgumentException("Cash switching requires a booking");
        }
        Booking booking = bookingRepository.findByIdForUpdate(snapshot.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", snapshot.getBooking().getId()));
        Payment payment = paymentRepository.findByIdForUpdate(id).orElseThrow();
        if (payment.getStatus() == PaymentStatus.PAID || payment.getPaymentMethod() == PaymentMethod.CASH) {
            return paymentMapper.toResponseDto(payment);
        }
        PaymentResponseDto verification = withVerificationLock(id,
                () -> checkStatusInternal(id, PaymentVerificationSource.MANUAL));
        if (verification.status() == PaymentStatus.PAID) return verification;
        payment = paymentRepository.findByIdForUpdate(id).orElseThrow();
        if (payment.getLastVerificationError() != null) {
            throw new IllegalStateException("Payment verification is unavailable. Check again before switching to cash");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only a pending booking can switch to cash");
        }
        ensureBookingHoldActive(booking);
        for (Payment existing : paymentRepository.findByBookingIdAndStatusOrderByIdAsc(booking.getId(), PaymentStatus.PENDING)) {
            if (existing.getPaymentMethod() == PaymentMethod.CASH) return paymentMapper.toResponseDto(existing);
        }
        // Keep the old QR and its audit trail available for late-payment reconciliation.
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
        markPendingTransactions(payment, PaymentStatus.FAILED);

        return create(new PaymentRequestDto(
                payment.getAmount(),
                PaymentMethod.CASH,
                payment.getCustomer().getId(),
                payment.getBooking() != null ? payment.getBooking().getId() : null,
                payment.getOrder() != null ? payment.getOrder().getId() : null,
                null,
                null,
                null
        ));
    }

    private PaymentResponseDto checkStatusInternal(Long id, PaymentVerificationSource source) {
        Payment snapshot = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        if (snapshot.getBooking() != null) {
            bookingRepository.findByIdForUpdate(snapshot.getBooking().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", snapshot.getBooking().getId()));
        }
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        if (payment.getStatus() == PaymentStatus.PAID) {
            activateMembershipIfNeeded(payment);
            return paymentMapper.toResponseDto(payment);
        }
        // PAID, FAILED and EXPIRED are terminal for provider polling. Late
        // reconciliation must be an explicit administrative workflow.
        if (!shouldCheckBakong(payment)) {
            return paymentMapper.toResponseDto(payment);
        }

        LocalDateTime now = LocalDateTime.now(PHNOM_PENH_ZONE);
        ensureLegacyVerificationState(payment, now);
        if (!isVerificationDue(payment, source, now)) {
            return paymentMapper.toResponseDto(paymentRepository.save(payment));
        }

        payment.setLastVerificationAt(now);
        payment.setVerificationAttemptCount(payment.getVerificationAttemptCount() + 1);
        if (source == PaymentVerificationSource.MANUAL) {
            payment.setManualVerificationCount(payment.getManualVerificationCount() + 1);
        } else if (source == PaymentVerificationSource.SCHEDULED && !isExpired(payment, now)) {
            payment.setScheduledVerificationCount(payment.getScheduledVerificationCount() + 1);
        }
        paymentRepository.save(payment);

        log.info("Bakong verification started: paymentId={}, source={}, attempt={}, manualAttempts={}",
                payment.getId(), source, payment.getVerificationAttemptCount(), payment.getManualVerificationCount());
        BakongCheckResult result = bakongService.checkTransactionByMd5(payment.getMd5Hash());
        log.info("Bakong verification finished: paymentId={}, status={}, authoritative={}, retryable={}, rateLimited={}",
                payment.getId(), result.status(), result.authoritative(), result.retryable(), result.rateLimited());

        if (result.rateLimited()) {
            LocalDateTime cooldown = bakongRequestBudgetService.markRateLimited(result.retryAfterSeconds());
            payment.setRateLimitedUntil(cooldown);
            payment.setNextVerificationAt(cooldown);
            payment.setLastVerificationError("Bakong verification is temporarily unavailable. Please try again later.");
            paymentRepository.save(payment);
            return paymentMapper.toResponseDto(payment);
        }

        payment.setRateLimitedUntil(null);
        if (result.paid()) {
            if (isValidBakongConfirmation(payment, result)) {
                payment.setLastVerificationError(null);
                paymentRepository.save(payment);
                log.info("Bakong payment confirmed: paymentId={}, bookingId={}, transactionId={}, md5={}",
                        payment.getId(), payment.getBooking() != null ? payment.getBooking().getId() : null,
                        payment.getTransactionId(), maskHash(payment.getMd5Hash()));
                return confirmPaymentInternal(id, false);
            }
            result = BakongCheckResult.verificationError(payment.getMd5Hash(),
                    "Bakong confirmation did not match the payment record");
        }

        if (!result.authoritative()) {
            recordTemporaryVerificationError(payment, result, now);
            return paymentMapper.toResponseDto(paymentRepository.save(payment));
        }

        payment.setLastVerificationError(null);
        payment.setVerificationFailureCount(0);
        payment.setNextVerificationAt(nextScheduledVerificationAt(payment, now));
        paymentRepository.save(payment);

        if (isExpired(payment, now)) {
            return expirePaymentInternal(payment);
        }
        return paymentMapper.toResponseDto(payment);
    }

    private void markPendingAttemptFailed(Payment payment) {
        markPendingTransactions(payment, PaymentStatus.FAILED);
    }

    private PaymentResponseDto withVerificationLock(Long paymentId, Supplier<PaymentResponseDto> action) {
        ReentrantLock lock = verificationLocks.computeIfAbsent(paymentId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.debug("Duplicate Bakong verification blocked by per-payment lock: paymentId={}", paymentId);
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
            return paymentMapper.toResponseDto(payment);
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                verificationLocks.remove(paymentId, lock);
            }
        }
    }

    private boolean isVerificationDue(Payment payment, PaymentVerificationSource source, LocalDateTime now) {
        if (payment.getRateLimitedUntil() != null && now.isBefore(payment.getRateLimitedUntil())) {
            return false;
        }
        if (source == PaymentVerificationSource.MANUAL
                && payment.getManualVerificationCount() >= MAX_MANUAL_CHECKS) {
            payment.setLastVerificationError("The manual payment check limit has been reached.");
            return false;
        }
        if (source == PaymentVerificationSource.FINAL && !isExpired(payment, now)) {
            return false;
        }
        if (source == PaymentVerificationSource.SCHEDULED
                && payment.getNextVerificationAt() != null
                && now.isBefore(payment.getNextVerificationAt())) {
            return false;
        }
        if (source != PaymentVerificationSource.FINAL && source != PaymentVerificationSource.SYSTEM
                && payment.getLastVerificationAt() != null) {
            int interval = Math.max(1, khqrConfig.getMinVerificationIntervalSeconds());
            if (now.isBefore(payment.getLastVerificationAt().plusSeconds(interval))) {
                return false;
            }
        }
        return true;
    }

    private void recordTemporaryVerificationError(
            Payment payment,
            BakongCheckResult result,
            LocalDateTime now
    ) {
        int failures = payment.getVerificationFailureCount() + 1;
        payment.setVerificationFailureCount(failures);
        payment.setLastVerificationError(result.message() != null && !result.message().isBlank()
                ? result.message()
                : "Bakong verification is temporarily unavailable. Please try again later.");

        int base = Math.max(1, khqrConfig.getTemporaryErrorBackoffSeconds());
        int maximum = Math.max(base, khqrConfig.getMaxTemporaryErrorBackoffSeconds());
        long exponential = Math.min(maximum, base * (1L << Math.min(failures - 1, 10)));
        long retryAfter = result.retryAfterSeconds() != null ? result.retryAfterSeconds() : 0L;
        LocalDateTime retryAt = now.plusSeconds(Math.max(exponential, retryAfter));
        payment.setNextVerificationAt(retryAt);
        log.warn("Bakong verification deferred after temporary error: paymentId={}, failures={}, nextVerificationAt={}",
                payment.getId(), failures, retryAt);
    }

    private void initializeVerificationState(Payment payment, LocalDateTime now) {
        payment.setVerificationStartedAt(now);
        payment.setLastVerificationAt(null);
        payment.setNextVerificationAt(now.plusSeconds(SCHEDULED_VERIFICATION_SECONDS[0]));
        payment.setVerificationAttemptCount(0);
        payment.setScheduledVerificationCount(0);
        payment.setManualVerificationCount(0);
        payment.setVerificationFailureCount(0);
        payment.setLastVerificationError(null);
        payment.setRateLimitedUntil(null);
    }

    private void clearVerificationState(Payment payment) {
        payment.setVerificationStartedAt(null);
        payment.setLastVerificationAt(null);
        payment.setNextVerificationAt(null);
        payment.setVerificationAttemptCount(0);
        payment.setScheduledVerificationCount(0);
        payment.setManualVerificationCount(0);
        payment.setVerificationFailureCount(0);
        payment.setLastVerificationError(null);
        payment.setRateLimitedUntil(null);
    }

    private void ensureLegacyVerificationState(Payment payment, LocalDateTime now) {
        if (payment.getVerificationStartedAt() == null) {
            // Existing rows predate persisted scheduling metadata. Give each
            // one a single due slot, then move it onto the normal cadence.
            payment.setVerificationStartedAt(now.minusSeconds(SCHEDULED_VERIFICATION_SECONDS[0]));
        }
        if (payment.getNextVerificationAt() == null && !isExpired(payment, now)) {
            payment.setNextVerificationAt(now);
        }
    }

    private LocalDateTime nextScheduledVerificationAt(Payment payment, LocalDateTime now) {
        LocalDateTime startedAt = payment.getVerificationStartedAt() != null
                ? payment.getVerificationStartedAt() : now;
        for (int seconds : SCHEDULED_VERIFICATION_SECONDS) {
            LocalDateTime candidate = startedAt.plusSeconds(seconds);
            if (candidate.isAfter(now)) return candidate;
        }
        if (payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(now)) {
            return payment.getExpiresAt();
        }
        return now;
    }

    private boolean isExpired(Payment payment, LocalDateTime now) {
        return payment.getExpiresAt() != null && !now.isBefore(payment.getExpiresAt());
    }

    private void markPendingTransactions(Payment payment, PaymentStatus status) {
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByPaymentIdAndStatusForUpdate(payment.getId(), PaymentStatus.PENDING);
        transactions.forEach(transaction -> transaction.setStatus(status));
        paymentTransactionRepository.saveAll(transactions);
    }

    private boolean shouldCheckBakong(Payment payment) {
        if (payment.getPaymentMethod() != PaymentMethod.KHQR || payment.getMd5Hash() == null) {
            return false;
        }
        return payment.getStatus() == PaymentStatus.PENDING;
    }

    private PaymentResponseDto expirePaymentInternal(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return paymentMapper.toResponseDto(payment);
        }

        if (payment.getBooking() != null) {
            bookingService.expirePendingBooking(payment.getBooking().getId());
            Long paymentId = payment.getId();
            Payment expired = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
            return paymentMapper.toResponseDto(expired);
        }

        payment.setStatus(PaymentStatus.EXPIRED);
        cancelPendingMembershipPayment(payment);
        payment = paymentRepository.save(payment);
        markPendingTransactions(payment, PaymentStatus.EXPIRED);

        log.info("Payment expired without Bakong confirmation: paymentId={}", payment.getId());

        return paymentMapper.toResponseDto(payment);
    }

    private PaymentTransaction findOrCreateTransaction(Payment payment) {
        String reference = payment.getTransactionId();
        List<PaymentTransaction> transactions = reference == null
                ? List.of()
                : paymentTransactionRepository.findByPaymentIdAndReferenceForUpdate(payment.getId(), reference);
        PaymentTransaction transaction = transactions.isEmpty()
                ? paymentTransactionRepository.findByPaymentIdAndStatusForUpdate(payment.getId(), PaymentStatus.PENDING)
                .stream()
                .findFirst()
                .orElse(null)
                : transactions.get(0);

        if (transaction == null) {
            transaction = new PaymentTransaction();
            transaction.setPayment(payment);
            transaction.setBooking(payment.getBooking());
            transaction.setOrder(payment.getOrder());
            transaction.setAmount(payment.getAmount());
            transaction.setTransactionType(payment.getPaymentMethod());
            transaction.setReference(reference != null ? reference : "CONFIRM-" + payment.getId());
        }
        return transaction;
    }

    private void activateMembershipIfNeeded(Payment payment) {
        if (payment.getUserMembership() == null) {
            return;
        }
        UserMembership membership = userMembershipRepository.findByIdForUpdate(payment.getUserMembership().getId())
                .orElseThrow(() -> new ResourceNotFoundException("UserMembership", payment.getUserMembership().getId()));
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            return;
        }
        if (membership.getStatus() != MembershipStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("A payment cannot activate this membership");
        }
        boolean alreadyActive = userMembershipRepository
                .findByCustomerIdAndStatusForUpdate(membership.getCustomer().getId(), MembershipStatus.ACTIVE)
                .stream()
                .anyMatch(existing -> !existing.getId().equals(membership.getId()));
        if (alreadyActive) {
            throw new IllegalStateException("Customer already has an active membership");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setStartedAt(now);
        membership.setExpiresAt(now.plusMonths(membership.getDurationMonthsSnapshot()));
        userMembershipRepository.save(membership);
    }

    private void cancelPendingMembershipPayment(Payment payment) {
        if (payment.getUserMembership() == null) {
            return;
        }
        UserMembership membership = userMembershipRepository.findByIdForUpdate(payment.getUserMembership().getId())
                .orElseThrow(() -> new ResourceNotFoundException("UserMembership", payment.getUserMembership().getId()));
        if (membership.getStatus() == MembershipStatus.PENDING_PAYMENT) {
            membership.setStatus(MembershipStatus.CANCELLED);
            membership.setCancelledAt(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")));
            userMembershipRepository.save(membership);
        }
    }

    @Override
    @Transactional
    public PaymentResponseDto update(Long id, PaymentRequestDto dto) {
        Payment existing = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(existing.getCustomer());

        if (existing.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be modified");
        }

        User requestedCustomer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.customerId());
        ensureSameCustomer(requestedCustomer, existing.getCustomer(), "Payment");
        if (dto.paymentMethod() != existing.getPaymentMethod()) {
            throw new IllegalArgumentException("Payment method cannot change; create a new payment attempt");
        }
        if (dto.bookingId() == null ? existing.getBooking() != null
                : existing.getBooking() == null || !dto.bookingId().equals(existing.getBooking().getId())) {
            throw new IllegalArgumentException("Payment booking cannot change");
        }
        if (dto.orderId() == null ? existing.getOrder() != null
                : existing.getOrder() == null || !dto.orderId().equals(existing.getOrder().getId())) {
            throw new IllegalArgumentException("Payment order cannot change");
        }
        if (dto.userMembershipId() == null ? existing.getUserMembership() != null
                : existing.getUserMembership() == null || !dto.userMembershipId().equals(existing.getUserMembership().getId())) {
            throw new IllegalArgumentException("Payment membership cannot change");
        }

        BigDecimal expectedAmount = dto.amount();
        if (existing.getBooking() != null) {
            Long bookingId = existing.getBooking().getId();
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new IllegalStateException("Only pending bookings can have pending payments");
            }
            expectedAmount = bookingTotalService.recalculate(booking);
        }
        if (existing.getOrder() != null) {
            expectedAmount = expectedAmount.add(bookingTotalService.recalculateOrder(existing.getOrder()));
        }
        if (existing.getUserMembership() != null) {
            expectedAmount = existing.getUserMembership().getPriceSnapshot();
        }
        if ((existing.getBooking() != null || existing.getOrder() != null || existing.getUserMembership() != null)
                && dto.amount().compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("Payment amount must match the current payable total: " + expectedAmount);
        }
        if (existing.getPaymentMethod() == PaymentMethod.KHQR
                && existing.getKhqrString() != null
                && existing.getAmount().compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("KHQR amount cannot change; create a new payment attempt");
        }
        existing.setAmount(expectedAmount);
        existing = paymentRepository.save(existing);
        return paymentMapper.toResponseDto(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto getById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(payment.getCustomer());
        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getAll() {
        User currentUser = authorizationService.getCurrentUser();
        List<Payment> payments = authorizationService.isStaffOrAdmin(currentUser)
                ? paymentRepository.findAll()
                : paymentRepository.findByCustomerId(currentUser.getId());
        return payments.stream()
                .map(paymentMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        authorizationService.requireStaffOrAdmin();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        throw new IllegalStateException("Payment records are retained for audit and cannot be deleted");
    }

    private void ensureSameCustomer(User paymentCustomer, User linkedCustomer, String resourceName) {
        if (paymentCustomer == null
                || linkedCustomer == null
                || paymentCustomer.getId() == null
                || !paymentCustomer.getId().equals(linkedCustomer.getId())) {
            throw new IllegalArgumentException(resourceName + " does not belong to the payment customer");
        }
    }

    private void ensureBookingHoldActive(Booking booking) {
        if (booking.getExpiresAt() != null
                && !booking.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")))) {
            bookingService.expirePendingBooking(booking.getId());
            throw new IllegalStateException("Booking hold has expired");
        }
    }

    private void ensureSeatsCanBeConfirmed(Booking booking, List<BookingSeat> bookingSeats) {
        for (BookingSeat bookingSeat : bookingSeats) {
            if (bookingSeat.getSeat() == null || booking.getShow() == null) {
                throw new IllegalStateException("Booking seat data is incomplete");
            }
            seatRepository.findByIdForUpdate(bookingSeat.getSeat().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Seat", bookingSeat.getSeat().getId()));
            if (bookingSeatRepository.existsActiveReservationForShowSeat(
                    booking.getShow().getId(), bookingSeat.getSeat().getId(), bookingSeat.getId())) {
                throw new IllegalStateException("Booking seats are no longer available for late payment recovery");
            }
        }
    }

    private boolean isValidBakongConfirmation(Payment payment, BakongCheckResult result) {
        // Mock mode has no real Bakong transaction details. Production must
        // fail closed unless all merchant-owned fields can be verified.
        if (khqrConfig.isMockMode()) {
            return true;
        }

        if (result.amount() == null || result.currency() == null || result.currency().isBlank()
                || result.toAccountId() == null || result.toAccountId().isBlank()) {
            log.warn("Bakong confirmation data is incomplete: paymentId={}, md5={}",
                    payment.getId(), maskHash(payment.getMd5Hash()));
            return false;
        }
        if (payment.getAmount() == null || result.amount().compareTo(payment.getAmount()) != 0) {
            log.warn("Bakong amount mismatch: paymentId={}, expectedAmount={}, receivedAmount={}",
                    payment.getId(), payment.getAmount(), result.amount());
            return false;
        }

        String expectedCurrency = khqrConfig.getCurrency() == null || khqrConfig.getCurrency().isBlank()
                ? "USD"
                : khqrConfig.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!expectedCurrency.equalsIgnoreCase(result.currency().trim())) {
            log.warn("Bakong currency mismatch: paymentId={}, expectedCurrency={}, receivedCurrency={}",
                    payment.getId(), expectedCurrency, result.currency());
            return false;
        }

        String expectedAccount = khqrConfig.getAccountId();
        if (expectedAccount == null || expectedAccount.isBlank()
                || !expectedAccount.trim().equalsIgnoreCase(result.toAccountId().trim())) {
            log.warn("Bakong merchant account mismatch: paymentId={}, expectedAccount={}, receivedAccount={}",
                    payment.getId(), maskAccount(expectedAccount), maskAccount(result.toAccountId()));
            return false;
        }
        return true;
    }

    private String maskAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return "<missing>";
        }
        String normalized = accountId.trim();
        int separator = normalized.indexOf('@');
        if (separator <= 1) {
            return "***";
        }
        return normalized.substring(0, 2) + "***" + normalized.substring(separator);
    }

    private String maskHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "<missing>";
        }
        String normalized = hash.trim();
        return normalized.length() <= 8
                ? "****"
                : normalized.substring(0, 4) + "..." + normalized.substring(normalized.length() - 4);
    }
}
