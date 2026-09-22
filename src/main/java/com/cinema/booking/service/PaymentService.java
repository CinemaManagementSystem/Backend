package com.cinema.booking.service;

import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import java.util.List;
import com.cinema.booking.enums.PaymentVerificationSource;

public interface PaymentService {

    PaymentResponseDto create(PaymentRequestDto dto);
    PaymentResponseDto update(Long id, PaymentRequestDto dto);
    PaymentResponseDto getById(Long id);
    List<PaymentResponseDto> getAll();
    PaymentResponseDto confirmPayment(Long id);
    PaymentResponseDto confirmPaymentFromSystem(Long id);
    PaymentResponseDto checkStatus(Long id);
    PaymentResponseDto checkStatus(Long id, PaymentVerificationSource source);
    PaymentResponseDto checkStatusFromSystem(Long id);
    PaymentResponseDto checkStatusFromSystem(Long id, PaymentVerificationSource source);
    PaymentResponseDto switchToCash(Long id);
    void delete(Long id);
}
