package com.cinema.booking.dto.membership;

import com.cinema.booking.dto.payments.PaymentResponseDto;

public record MembershipSubscribeResponseDto(
        UserMembershipResponseDto membership,
        PaymentResponseDto payment
) {
}
