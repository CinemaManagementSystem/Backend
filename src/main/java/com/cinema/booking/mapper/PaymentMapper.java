package com.cinema.booking.mapper;

import com.cinema.booking.entity.Payment;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public Payment toEntity(PaymentRequestDto dto) {
        Payment payment = new Payment();
        payment.setAmount(dto.amount());
        payment.setPaymentMethod(dto.paymentMethod());
        payment.setStatus(PaymentStatus.PENDING);

        // Status, paidAt, transactionId, khqrString, md5Hash, expiresAt are populated in Service layer
        return payment;
    }

    public PaymentResponseDto toResponseDto(Payment payment) {
        return new PaymentResponseDto(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getPaidAt(),
                payment.getExpiresAt(),
                payment.getKhqrString(),
                payment.getMd5Hash(),
                payment.getBooking() != null ? payment.getBooking().getId() : null,
                payment.getCustomer() != null ? payment.getCustomer().getId() : null,
                payment.getOrder() != null ? payment.getOrder().getId() : null
        );
    }
}