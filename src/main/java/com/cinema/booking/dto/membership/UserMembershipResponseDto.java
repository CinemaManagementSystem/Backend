package com.cinema.booking.dto.membership;

import com.cinema.booking.enums.MembershipStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserMembershipResponseDto(
        UUID id,
        Long customerId,
        UUID planId,
        String planName,
        String planCode,
        MembershipStatus status,
        BigDecimal priceSnapshot,
        Integer durationMonthsSnapshot,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        Long paymentId
) {
}
