package com.cinema.booking.service;

import com.cinema.booking.dto.auth.GoogleAuthRequestDto;

public interface GoogleAuthService {

    record GoogleUserInfo(
            String sub,
            String email,
            String name,
            String picture,
            boolean emailVerified
    ) {}

    GoogleUserInfo verifyToken(GoogleAuthRequestDto dto);

}
