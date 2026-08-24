package com.cinema.booking.mapper;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public Payment toEntity(PaymentRequestDto dto) {
        Payment payment = new Payment();
        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        // Status, paidAt, transactionId, khqrString, md5Hash, expiresAt are populated in Service layer
        return payment;
    }

    public PaymentResponseDto toResponseDto(Payment payment) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.setId(payment.getId());
        dto.setAmount(payment.getAmount());
        dto.setPaidAt(payment.getPaidAt());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setStatus(payment.getStatus());
        dto.setTransactionId(payment.getTransactionId());
        dto.setKhqrString(payment.getKhqrString());
        dto.setMd5Hash(payment.getMd5Hash());
        dto.setExpiresAt(payment.getExpiresAt());
        dto.setBookingId(payment.getBooking() != null ? payment.getBooking().getId() : null);
        dto.setCustomerId(payment.getCustomer() != null ? payment.getCustomer().getId() : null);
        dto.setOrderId(payment.getOrder() != null ? payment.getOrder().getId() : null);
        return dto;
    }
}