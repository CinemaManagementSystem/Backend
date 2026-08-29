package com.cinema.booking.dto.bookings;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingSeatRequestDto {

    @NotNull
    private Long bookingId;
    @NotNull
    private Long seatId;
}
