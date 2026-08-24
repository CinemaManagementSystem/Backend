package com.cinema.booking.dto.payments;

import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponseDto {

    private Long id;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String transactionId;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;

    // KHQR-specific (null for CASH)
    private String khqrString;
    private String md5Hash;

    // FK IDs
    private Long bookingId;
    private Long customerId;
    private Long orderId;
}