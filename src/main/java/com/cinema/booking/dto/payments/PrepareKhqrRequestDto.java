package com.cinema.booking.dto.payments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PrepareKhqrRequestDto(
        @NotBlank @Size(max = 2048) String qr,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String md5,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String expectedMd5
) {}
