package com.cinema.booking.service.impl;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.dto.payments.BakongCheckResult;
import com.cinema.booking.dto.payments.KhqrPayload;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Order;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.User;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.PaymentMapper;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BakongService;
import com.cinema.booking.service.PaymentService;
import com.cinema.booking.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;
    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final BakongService bakongService;
    private final KhqrConfig khqrConfig;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public PaymentResponseDto create(PaymentRequestDto dto) {
        Payment payment = paymentMapper.toEntity(dto);

        User customer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.customerId());
        payment.setCustomer(customer);

        Booking booking = null;
        if (dto.bookingId() != null) {
            booking = bookingRepository.findById(dto.bookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.bookingId()));
            authorizationService.requireOwnerOrStaff(booking.getCustomer());
            ensureSameCustomer(customer, booking.getCustomer(), "Booking");
            payment.setBooking(booking);
        }

        Order order = null;
        if (dto.orderId() != null) {
            order = orderRepository.findById(dto.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.orderId()));
            authorizationService.requireOwnerOrStaff(order.getCustomer());
            ensureSameCustomer(customer, order.getCustomer(), "Order");
            payment.setOrder(order);
        }

        PaymentMethod method = dto.paymentMethod();
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PENDING);

        String txId = "TXN-" + method.name() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        if (method == PaymentMethod.KHQR) {
            // Generate standard EMVCo/NBC KHQR payload and MD5 hash
            payment.setTransactionId(txId);
            String currency = khqrConfig.getCurrency() != null ? khqrConfig.getCurrency() : "USD";
            KhqrPayload khqr = bakongService.generateDynamicKhqr(
                    dto.amount(),
                    currency,
                    txId,
                    "Cinema Booking",
                    dto.accountId(),
                    dto.merchantName()
            );
            payment.setKhqrString(khqr.khqrString());
            payment.setMd5Hash(khqr.md5Hash());
            payment.setExpiresAt(khqr.expiresAt());
        } else {
            // CASH payment at cinema counter — no gateway reference, nothing to expire
            payment.setTransactionId(null);
            payment.setKhqrString(null);
            payment.setMd5Hash(null);
            payment.setExpiresAt(null);
        }

        payment = paymentRepository.save(payment);

        // Record initial pending payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setBooking(booking);
        transaction.setOrder(order);
        transaction.setAmount(dto.amount());
        transaction.setTransactionType(method);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setReference(txId);
        paymentTransactionRepository.save(transaction);

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto confirmPayment(Long id) {
        if (SecurityUtil.getCurrentUsername().isPresent()) {
            authorizationService.requireStaffOrAdmin();
        }
        return confirmPaymentInternal(id);
    }

    private PaymentResponseDto confirmPaymentInternal(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        if (payment.getStatus() == PaymentStatus.PAID) {
            // Idempotency guard: confirming twice (e.g. staff click + Bakong poll racing)
            // must not create a duplicate PAID transaction log.
            return paymentMapper.toResponseDto(payment);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")));

        // Automatically update related Booking to CONFIRMED
        if (payment.getBooking() != null) {
            Booking booking = payment.getBooking();
            booking.transitionTo(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
        }

        // Automatically update related Order to PAID
        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            order.setStatus("PAID");
            orderRepository.save(order);
        }

        payment = paymentRepository.save(payment);

        // Record successful transaction log
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setBooking(payment.getBooking());
        transaction.setOrder(payment.getOrder());
        transaction.setAmount(payment.getAmount());
        transaction.setTransactionType(payment.getPaymentMethod());
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setReference(payment.getTransactionId() != null ? payment.getTransactionId() : "CONFIRM-" + System.currentTimeMillis());
        paymentTransactionRepository.save(transaction);

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatus(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(payment.getCustomer());

        if (payment.getStatus() == PaymentStatus.PENDING) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));

            // Check if payment has expired
            if (payment.getExpiresAt() != null && now.isAfter(payment.getExpiresAt())) {
                payment.setStatus(PaymentStatus.FAILED);
                payment = paymentRepository.save(payment);

                PaymentTransaction transaction = new PaymentTransaction();
                transaction.setPayment(payment);
                transaction.setBooking(payment.getBooking());
                transaction.setOrder(payment.getOrder());
                transaction.setAmount(payment.getAmount());
                transaction.setTransactionType(payment.getPaymentMethod());
                transaction.setStatus(PaymentStatus.FAILED);
                transaction.setReference("EXPIRED-" + payment.getTransactionId());
                paymentTransactionRepository.save(transaction);

                return paymentMapper.toResponseDto(payment);
            }

            // If KHQR payment is not expired, verify against Bakong Network
            if (payment.getPaymentMethod() == PaymentMethod.KHQR && payment.getMd5Hash() != null) {
                BakongCheckResult result = bakongService.checkTransactionByMd5(payment.getMd5Hash());
                if (result.paid()) {
                    log.info("Payment #{} verified as PAID via Bakong MD5 check", id);
                    return confirmPaymentInternal(id);
                }
            }
        }

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto update(Long id, PaymentRequestDto dto) {
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(existing.getCustomer());

        if (existing.getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot modify a payment that has already succeeded");
        }

        existing.setAmount(dto.amount());
        existing.setPaymentMethod(dto.paymentMethod());

        if (dto.customerId() != null) {
            existing.setCustomer(authorizationService.resolveCustomerForAuthenticatedRequest(dto.customerId()));
        }
        if (dto.bookingId() != null) {
            Booking booking = bookingRepository.findById(dto.bookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.bookingId()));
            authorizationService.requireOwnerOrStaff(booking.getCustomer());
            ensureSameCustomer(existing.getCustomer(), booking.getCustomer(), "Booking");
            existing.setBooking(booking);
        }
        if (dto.orderId() != null) {
            Order order = orderRepository.findById(dto.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.orderId()));
            authorizationService.requireOwnerOrStaff(order.getCustomer());
            ensureSameCustomer(existing.getCustomer(), order.getCustomer(), "Order");
            existing.setOrder(order);
        }
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
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        authorizationService.requireOwnerOrStaff(payment.getCustomer());
        paymentRepository.delete(payment);
    }

    private void ensureSameCustomer(User paymentCustomer, User linkedCustomer, String resourceName) {
        if (paymentCustomer == null
                || linkedCustomer == null
                || paymentCustomer.getId() == null
                || !paymentCustomer.getId().equals(linkedCustomer.getId())) {
            throw new IllegalArgumentException(resourceName + " does not belong to the payment customer");
        }
    }
}
