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
        dto.setOriginalPrice(bookingSeat.getOriginalPrice());
        dto.setMembershipDiscountAmount(bookingSeat.getMembershipDiscountAmount());
        dto.setMembershipBenefitCode(bookingSeat.getMembershipBenefitCode());
        dto.setStatus(bookingSeat.getStatus());
        dto.setExpiresAt(bookingSeat.getExpiresAt());
        dto.setBookingId(bookingSeat.getBooking() != null ? bookingSeat.getBooking().getId() : null);
        dto.setSeatId(bookingSeat.getSeat() != null ? bookingSeat.getSeat().getId() : null);
        dto.setUserMembershipId(bookingSeat.getUserMembership() != null ? bookingSeat.getUserMembership().getId() : null);
        return dto;
    }
}
