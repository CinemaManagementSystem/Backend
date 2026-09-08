package com.cinema.booking.controller.auth;

import com.cinema.booking.dto.auth.LoginRequestDto;
import com.cinema.booking.dto.auth.LogoutRequestDto;
import com.cinema.booking.dto.auth.LogoutResponseDto;
import com.cinema.booking.dto.auth.RefreshTokenRequestDto;
import com.cinema.booking.dto.auth.RegisterRequestDto;
import com.cinema.booking.dto.auth.AuthResponseDto;
import com.cinema.booking.dto.auth.RegisterResponseDto;
import com.cinema.booking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService; 

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto dto) {
        return new ResponseEntity<>(
                authService.register(dto), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto dto) {
        return ResponseEntity.ok(authService.refresh(dto));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDto> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) LogoutRequestDto dto) {
        return ResponseEntity.ok(authService.logout(authorizationHeader, dto));
    }
}
