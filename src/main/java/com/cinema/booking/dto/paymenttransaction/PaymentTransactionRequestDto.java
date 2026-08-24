package com.cinema.booking.dto.paymenttransaction;

import com.cinema.booking.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentTransactionRequestDto {

    @NotNull @Positive
    private BigDecimal amount;

    @NotNull
    private PaymentMethod transactionType;     // CASH | KHQR

    private String reference;

    @NotNull
    private Long paymentId;

    private Long bookingId;

    private Long orderId;
}
