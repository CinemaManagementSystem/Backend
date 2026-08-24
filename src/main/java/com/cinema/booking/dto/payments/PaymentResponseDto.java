package com.cinema.booking.dto.payments;

import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDto(

        Long id,

        BigDecimal amount,

        PaymentMethod paymentMethod,

        PaymentStatus status,

        String transactionId,

        LocalDateTime paidAt,

        LocalDateTime expiresAt,

        // KHQR-specific
        String khqrString,

        String md5Hash,

        // FK IDs
        Long bookingId,

        Long customerId,

        Long orderId

) {
}