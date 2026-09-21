package com.cinema.booking.dto.membership;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record MembershipPlanRequestDto(
        @NotBlank String name,
        @NotBlank String code,
        String description,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer durationMonths,
        Boolean active,
        @NotNull @PositiveOrZero Integer sortOrder,
        @Valid List<MembershipBenefitDto> benefits
) {
}
