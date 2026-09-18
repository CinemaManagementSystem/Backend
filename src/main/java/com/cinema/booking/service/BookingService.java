package com.cinema.booking.service;

import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import java.util.List;

public interface BookingService {

    BookingResponseDto create(BookingRequestDto dto);
    BookingResponseDto update(Long id, BookingRequestDto dto);
    BookingResponseDto getById(Long id);
    List<BookingResponseDto> getAll();
    void delete(Long id);
    void expirePendingBooking(Long id);
}
