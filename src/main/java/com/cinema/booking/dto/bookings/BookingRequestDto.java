package com.cinema.booking.dto.bookings;

import jakarta.validation.constraints.*;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingRequestDto {

    private LocalDateTime bookedAt;
    private String bookingCode;
    // Kept for request compatibility. The service always recalculates this value from booking seats.
    private BigDecimal totalAmount;
    @NotNull
    private Long customerId;
    @NotNull
    private Long showId;
}
