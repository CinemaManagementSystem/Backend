package com.cinema.booking.dto.membership;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MembershipExtendRequestDto(
        @NotNull @Min(1) Integer months
) {
}
