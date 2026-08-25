package com.cinema.booking.dto.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KhqrPayload(
        String khqrString,
        String md5Hash,
        LocalDateTime expiresAt,
        BigDecimal amount,
        String currency,
        String billNumber
) {
}
