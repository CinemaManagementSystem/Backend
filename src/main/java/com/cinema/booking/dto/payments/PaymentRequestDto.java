package com.cinema.booking.dto.payments;

import com.cinema.booking.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequestDto {

    @NotNull @Positive
    private BigDecimal amount;

    @NotNull
    private PaymentMethod paymentMethod;   // CASH or KHQR

    @NotNull
    private Long customerId;

    private Long bookingId;         // nullable (food-only order)

    private Long orderId;           // nullable
}