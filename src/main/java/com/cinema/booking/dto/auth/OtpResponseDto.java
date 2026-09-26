package com.cinema.booking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponseDto {

    private String message;
    private String email;
    private String devOtp;
    private long expiresInSeconds;

}
