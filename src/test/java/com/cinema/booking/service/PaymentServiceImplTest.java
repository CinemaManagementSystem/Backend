package com.cinema.booking.service;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.mapper.PaymentMapper;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BakongService bakongService;
    @Mock private BookingTotalService bookingTotalService;
    @Mock private BookingService bookingService;
    private KhqrConfig khqrConfig;
    @Mock private AuthorizationService authorizationService;

    private PaymentServiceImpl paymentService;
    private Payment payment;

    @BeforeEach
    void setUp() {
        khqrConfig = new KhqrConfig();
        khqrConfig.setMockMode(false);
        khqrConfig.setCurrency("USD");
        khqrConfig.setAccountId("cinema@bakong");
        paymentService = new PaymentServiceImpl(
                paymentRepository, paymentTransactionRepository, paymentMapper,
                bookingRepository, bookingSeatRepository, seatRepository, orderRepository,
                bakongService, bookingTotalService, bookingService, khqrConfig, authorizationService);

        payment = new Payment();
        payment.setId(37L);
        payment.setAmount(new BigDecimal("12.50"));
        payment.setPaymentMethod(PaymentMethod.KHQR);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMd5Hash("abcdef1234567890abcdef1234567890");
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(paymentRepository.findById(37L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(37L)).thenReturn(Optional.of(payment));
    }

    @Test
    void amountMismatchDoesNotConfirmPayment() {
        assertRejected(BakongCheckResult.paid(
                "bakong-hash", "customer@bakong", "cinema@bakong",
                new BigDecimal("12.51"), "USD"));
    }

    @Test
    void currencyMismatchDoesNotConfirmPayment() {
        assertRejected(BakongCheckResult.paid(
                "bakong-hash", "customer@bakong", "cinema@bakong",
                new BigDecimal("12.50"), "KHR"));
    }

    @Test
    void merchantAccountMismatchDoesNotConfirmPayment() {
        assertRejected(BakongCheckResult.paid(
                "bakong-hash", "customer@bakong", "attacker@bakong",
                new BigDecimal("12.50"), "USD"));
    }

    @Test
    void incompleteBakongDataDoesNotConfirmPayment() {
        assertRejected(BakongCheckResult.paid(
                "bakong-hash", "customer@bakong", null, null, null));
    }

    private void assertRejected(BakongCheckResult result) {
        when(bakongService.checkTransactionByMd5(payment.getMd5Hash())).thenReturn(result);

        paymentService.checkStatusFromSystem(payment.getId());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentTransactionRepository, never()).save(any());
    }
}
