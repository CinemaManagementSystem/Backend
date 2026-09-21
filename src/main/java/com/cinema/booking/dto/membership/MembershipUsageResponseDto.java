package com.cinema.booking.dto.membership;

import com.cinema.booking.enums.MembershipBenefitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MembershipUsageResponseDto(
        UUID id,
        UUID userMembershipId,
        MembershipBenefitType benefitType,
        String usagePeriod,
        String sourceType,
        Long sourceId,
        BigDecimal discountAmount,
        LocalDateTime createdAt
) {
}
