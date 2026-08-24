package com.cinema.booking.dto.paymenttransaction;

import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentTransactionResponseDto {

    private Long id;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod transactionType;
    private String reference;
    private LocalDateTime createdAt;
    private Long paymentId;
    private Long bookingId;
    private Long orderId;
}
