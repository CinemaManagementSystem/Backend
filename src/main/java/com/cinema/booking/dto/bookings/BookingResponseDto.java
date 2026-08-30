package com.cinema.booking.dto.bookings;

import lombok.Data;
import com.cinema.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingResponseDto {

    private Long id;

    private LocalDateTime bookedAt;
    private String bookingCode;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private Long customerId;
    private Long showId;
}