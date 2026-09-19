package com.cinema.booking.service;

import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.dto.payments.VerifyKhqrRequestDto;
import com.cinema.booking.dto.payments.PrepareKhqrRequestDto;
import java.util.List;

public interface PaymentService {

    PaymentResponseDto create(PaymentRequestDto dto);
    PaymentResponseDto update(Long id, PaymentRequestDto dto);
    PaymentResponseDto getById(Long id);
    List<PaymentResponseDto> getAll();
    PaymentResponseDto confirmPayment(Long id);
    PaymentResponseDto confirmPaymentFromSystem(Long id);
    PaymentResponseDto checkStatus(Long id);
    PaymentResponseDto checkStatusFromSystem(Long id);
    PaymentResponseDto verifyKhqr(VerifyKhqrRequestDto request);
    PaymentResponseDto switchToCash(Long id);
    PaymentResponseDto prepareKhqr(Long id, PrepareKhqrRequestDto request);
    void delete(Long id);
}
