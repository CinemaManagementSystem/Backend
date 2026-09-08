package com.cinema.booking.service;

import com.cinema.booking.dto.auth.LoginRequestDto;
import com.cinema.booking.dto.auth.LogoutRequestDto;
import com.cinema.booking.dto.auth.LogoutResponseDto;
import com.cinema.booking.dto.auth.RefreshTokenRequestDto;
import com.cinema.booking.dto.auth.RegisterRequestDto;
import com.cinema.booking.dto.auth.AuthResponseDto;
import com.cinema.booking.dto.auth.RegisterResponseDto;

public interface AuthService {

    RegisterResponseDto register(RegisterRequestDto dto);

    AuthResponseDto login(LoginRequestDto dto);

    AuthResponseDto refresh(RefreshTokenRequestDto dto);

    LogoutResponseDto logout(String authorizationHeader, LogoutRequestDto dto);
}
