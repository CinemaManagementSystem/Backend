package com.cinema.booking.service.impl;

import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Order;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.PaymentTransactionMapper;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.OrderRepository;
import com.cinema.booking.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public PaymentTransactionResponseDto create(PaymentTransactionRequestDto dto) {
        PaymentTransaction transaction = paymentTransactionMapper.toEntity(dto);

        Payment payment = paymentRepository.findById(dto.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", dto.getPaymentId()));
        transaction.setPayment(payment);

        if (dto.getBookingId() != null) {
            Booking booking = bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
            transaction.setBooking(booking);
        }
        if (dto.getOrderId() != null) {
            Order order = orderRepository.findById(dto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));
            transaction.setOrder(order);
        }

        transaction = paymentTransactionRepository.save(transaction);
        return paymentTransactionMapper.toResponseDto(transaction);
    }

    @Override
    @Transactional
    public PaymentTransactionResponseDto update(Long id, PaymentTransactionRequestDto dto) {
        PaymentTransaction existing = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", id));
        existing.setAmount(dto.getAmount());
        existing.setTransactionType(dto.getTransactionType());
        existing.setReference(dto.getReference());

        Payment payment = paymentRepository.findById(dto.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", dto.getPaymentId()));
        existing.setPayment(payment);

        if (dto.getBookingId() != null) {
            existing.setBooking(bookingRepository.findById(dto.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId())));
        }
        if (dto.getOrderId() != null) {
            existing.setOrder(orderRepository.findById(dto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId())));
        }

        existing = paymentTransactionRepository.save(existing);
        return paymentTransactionMapper.toResponseDto(existing);
    }

    @Override
    public PaymentTransactionResponseDto getById(Long id) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", id));
        return paymentTransactionMapper.toResponseDto(transaction);
    }

    @Override
    public List<PaymentTransactionResponseDto> getAll() {
        return paymentTransactionRepository.findAll().stream()
                .map(paymentTransactionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentTransactionResponseDto> getByPaymentId(Long paymentId) {
        return paymentTransactionRepository.findByPaymentId(paymentId).stream()
                .map(paymentTransactionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!paymentTransactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("PaymentTransaction", id);
        }
        paymentTransactionRepository.deleteById(id);
    }
}
