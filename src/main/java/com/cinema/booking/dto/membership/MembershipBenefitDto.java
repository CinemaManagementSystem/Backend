package com.cinema.booking.dto.membership;

import com.cinema.booking.enums.MembershipBenefitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record MembershipBenefitDto(
        UUID id,
        @NotNull MembershipBenefitType benefitType,
        @NotNull @PositiveOrZero BigDecimal value,
        Integer monthlyLimit,
        Boolean active
) {
}
