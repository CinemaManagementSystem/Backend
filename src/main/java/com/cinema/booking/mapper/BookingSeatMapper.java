package com.cinema.booking.mapper;

import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.dto.bookings.BookingSeatRequestDto;
import com.cinema.booking.dto.bookings.BookingSeatResponseDto;
import org.springframework.stereotype.Component;

@Component
public class BookingSeatMapper {

    public BookingSeat toEntity(BookingSeatRequestDto dto) {
        return new BookingSeat();
    }

    public BookingSeatResponseDto toResponseDto(BookingSeat bookingSeat) {
        BookingSeatResponseDto dto = new BookingSeatResponseDto();
        dto.setId(bookingSeat.getId());
        dto.setPrice(bookingSeat.getPrice());
        dto.setStatus(bookingSeat.getStatus());
        dto.setBookingId(bookingSeat.getBooking() != null ? bookingSeat.getBooking().getId() : null);
        dto.setSeatId(bookingSeat.getSeat() != null ? bookingSeat.getSeat().getId() : null);
        return dto;
    }
}