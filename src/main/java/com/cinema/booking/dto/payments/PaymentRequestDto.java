package com.cinema.booking.dto.payments;

import com.cinema.booking.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequestDto(

        @NotNull
        @Positive
        BigDecimal amount,
        @NotNull
        PaymentMethod paymentMethod,
        @NotNull
        Long customerId,

        Long bookingId,

        Long orderId

) {
}