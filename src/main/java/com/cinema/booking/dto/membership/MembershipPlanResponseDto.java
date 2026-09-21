package com.cinema.booking.dto.membership;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MembershipPlanResponseDto(
        UUID id,
        String name,
        String code,
        String description,
        BigDecimal price,
        Integer durationMonths,
        Boolean active,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MembershipBenefitDto> benefits
) {
}
