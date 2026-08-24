package com.cinema.booking.dto.transaction;

import jakarta.validation.constraints.*;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WalletTransactionRequestDto {

    @NotNull @Positive

    private BigDecimal amount;
    @NotBlank
    private String reference;
    @NotBlank
    private String status;
    @NotBlank
    private String transactionType;
    @NotNull
    private Long bookingId;
    @NotNull
    private Long orderId;
    @NotNull
    private Long walletId;
}