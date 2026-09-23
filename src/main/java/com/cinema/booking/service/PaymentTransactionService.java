package com.cinema.booking.service;

import com.cinema.booking.dto.paymenttransaction.PaymentTransactionRequestDto;
import com.cinema.booking.dto.paymenttransaction.PaymentTransactionResponseDto;
import com.cinema.booking.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PaymentTransactionService {

    PaymentTransactionResponseDto create(PaymentTransactionRequestDto dto);
    PaymentTransactionResponseDto create(PaymentTransactionRequestDto dto, MultipartFile image);
    PaymentTransactionResponseDto update(Long id, PaymentTransactionRequestDto dto);
    PaymentTransactionResponseDto update(Long id, PaymentTransactionRequestDto dto, MultipartFile image);
    PaymentTransactionResponseDto getById(Long id);
    List<PaymentTransactionResponseDto> getAll();
    Page<PaymentTransactionResponseDto> getPage(PaymentStatus status, Pageable pageable);
    List<PaymentTransactionResponseDto> getByPaymentId(Long paymentId);
    void delete(Long id);
}
