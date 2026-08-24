package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.Order;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.PaymentMapper;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public PaymentResponseDto create(PaymentRequestDto dto) {
        Payment payment = paymentMapper.toEntity(dto);

        User customer = userRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getCustomerId()));
        payment.setCustomer(customer);

        Booking booking = null;
        if (dto.getBookingId() != null) {
            booking = bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
            payment.setBooking(booking);
        }

        Order order = null;
        if (dto.getOrderId() != null) {
            order = orderRepository.findById(dto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));
            payment.setOrder(order);
        }

        PaymentMethod method = dto.getPaymentMethod();
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PENDING);

        String txId = "TXN-" + method.name() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        payment.setTransactionId(txId);

        if (method == PaymentMethod.KHQR) {
            // Generate KHQR payload placeholder & MD5 hash with 10-minute expiry
            String randomHash = UUID.randomUUID().toString().replace("-", "");
            payment.setKhqrString("00020101021229300012bakong@dev0101" + randomHash.substring(0, 16) + "5406" + dto.getAmount() + "5802KH53038406304");
            payment.setMd5Hash(randomHash);
            payment.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")).plusMinutes(10));
        } else {
            // CASH payment at cinema counter
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
        transaction.setAmount(dto.getAmount());
        transaction.setTransactionType(method);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setReference(txId);
        paymentTransactionRepository.save(transaction);

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto confirmPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")));

        // Automatically update related Booking to CONFIRMED
        if (payment.getBooking() != null) {
            Booking booking = payment.getBooking();
            booking.setStatus("CONFIRMED");
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
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setReference(payment.getTransactionId() != null ? payment.getTransactionId() : "CONFIRM-" + System.currentTimeMillis());
        paymentTransactionRepository.save(transaction);

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto checkStatus(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        if (payment.getStatus() == PaymentStatus.PENDING && payment.getExpiresAt() != null) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
            if (now.isAfter(payment.getExpiresAt())) {
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
            }
        }

        return paymentMapper.toResponseDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto update(Long id, PaymentRequestDto dto) {
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        existing.setAmount(dto.getAmount());
        existing.setPaymentMethod(dto.getPaymentMethod());

        if (dto.getCustomerId() != null) {
            existing.setCustomer(userRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", dto.getCustomerId())));
        }
        if (dto.getBookingId() != null) {
            existing.setBooking(bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId())));
        }
        if (dto.getOrderId() != null) {
            existing.setOrder(orderRepository.findById(dto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId())));
        }
        existing = paymentRepository.save(existing);
        return paymentMapper.toResponseDto(existing);
    }

    @Override
    public PaymentResponseDto getById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        return paymentMapper.toResponseDto(payment);
    }

    @Override
    public List<PaymentResponseDto> getAll() {
        return paymentRepository.findAll().stream()
                .map(paymentMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payment", id);
        }
        paymentRepository.deleteById(id);
    }
}