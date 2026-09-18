package com.cinema.booking.mapper;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public Booking toEntity(BookingRequestDto dto) {
        Booking booking = new Booking();
        // bookedAt and bookingCode are server-owned values. They are generated
        // by BookingService so clients cannot extend a hold or choose a
        // predictable/colliding public reference.
        // totalAmount is derived by the service from booking_seats, never trusted from the request.
        // TODO: FK fields (customer, show) are resolved in the Service layer
        // using their respective repositories, then set on booking before saving.
        return booking;
    }

    public BookingResponseDto toResponseDto(Booking booking) {
        BookingResponseDto dto = new BookingResponseDto();
        dto.setId(booking.getId());
        dto.setBookedAt(booking.getBookedAt());
        dto.setExpiresAt(booking.getExpiresAt());
        dto.setBookingCode(booking.getBookingCode());
        dto.setStatus(booking.getStatus());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setCustomerId(booking.getCustomer() != null ? booking.getCustomer().getId() : null);
        dto.setShowId(booking.getShow() != null ? booking.getShow().getId() : null);
        return dto;
    }
}
