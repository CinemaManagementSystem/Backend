package com.cinema.booking.service;

import com.cinema.booking.dto.auth.OtpResponseDto;
import com.cinema.booking.dto.auth.OtpSendRequestDto;
import com.cinema.booking.dto.auth.OtpVerifyRequestDto;

public interface OtpService {

    OtpResponseDto sendOtp(OtpSendRequestDto dto);

    boolean verifyOtp(OtpVerifyRequestDto dto);

}
