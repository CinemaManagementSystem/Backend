package com.cinema.booking.dto.bookings;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingSeatResponseDto {

    private Long id;

    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal membershipDiscountAmount;
    private String membershipBenefitCode;
    private String status;
    private LocalDateTime expiresAt;
    private Long bookingId;
    private Long seatId;
    private java.util.UUID userMembershipId;
}
