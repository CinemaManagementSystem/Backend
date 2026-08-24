package com.cinema.booking.mapper;

import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;
import org.springframework.stereotype.Component;

@Component
public class PaymentTransactionMapper {

    public PaymentTransaction toEntity(PaymentTransactionRequestDto dto) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAmount(dto.getAmount());
        transaction.setTransactionType(dto.getTransactionType());
        transaction.setReference(dto.getReference());
        transaction.setStatus(PaymentStatus.PENDING);
        // FK fields (payment, booking, order) are resolved in the Service layer
        return transaction;
    }

    public PaymentTransactionResponseDto toResponseDto(PaymentTransaction transaction) {
        PaymentTransactionResponseDto dto = new PaymentTransactionResponseDto();
        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setStatus(transaction.getStatus());
        dto.setTransactionType(transaction.getTransactionType());
        dto.setReference(transaction.getReference());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setPaymentId(transaction.getPayment() != null ? transaction.getPayment().getId() : null);
        dto.setBookingId(transaction.getBooking() != null ? transaction.getBooking().getId() : null);
        dto.setOrderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null);
        return dto;
    }
}
