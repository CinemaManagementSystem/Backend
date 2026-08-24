package com.cinema.booking.service;

import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;

import java.util.List;

public interface PaymentTransactionService {

    PaymentTransactionResponseDto create(PaymentTransactionRequestDto dto);
    PaymentTransactionResponseDto update(Long id, PaymentTransactionRequestDto dto);
    PaymentTransactionResponseDto getById(Long id);
    List<PaymentTransactionResponseDto> getAll();
    List<PaymentTransactionResponseDto> getByPaymentId(Long paymentId);
    void delete(Long id);
}
