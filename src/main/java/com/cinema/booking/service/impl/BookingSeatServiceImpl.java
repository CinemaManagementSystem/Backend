package com.cinema.booking.service.impl;

import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Seat;
import com.cinema.booking.dto.bookings.BookingSeatRequestDto;
import com.cinema.booking.dto.bookings.BookingSeatResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.BookingSeatMapper;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.service.BookingSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingSeatServiceImpl implements BookingSeatService {

    private static final String DEFAULT_STATUS = "PENDING";

    private final BookingSeatRepository bookingSeatRepository;
    private final BookingSeatMapper bookingSeatMapper;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;

    @Override
    public BookingSeatResponseDto create(BookingSeatRequestDto dto) {
        Booking booking = bookingRepository.findById(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        Seat seat = seatRepository.findById(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));

        BookingSeat bookingSeat = bookingSeatMapper.toEntity(dto);
        bookingSeat.setBooking(booking);
        bookingSeat.setSeat(seat);
        bookingSeat.setPrice(seat.getPrice());
        bookingSeat.setStatus(DEFAULT_STATUS);
        bookingSeat = bookingSeatRepository.save(bookingSeat);
        return bookingSeatMapper.toResponseDto(bookingSeat);
    }

    @Override
    public BookingSeatResponseDto update(Long id, BookingSeatRequestDto dto) {
        BookingSeat existing = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        Booking booking = bookingRepository.findById(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        Seat seat = seatRepository.findById(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));

        BookingSeat updated = bookingSeatMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setBooking(booking);
        updated.setSeat(seat);
        updated.setPrice(resolvePrice(existing, seat));
        updated.setStatus(existing.getStatus());
        updated = bookingSeatRepository.save(updated);
        return bookingSeatMapper.toResponseDto(updated);
    }

    private BigDecimal resolvePrice(BookingSeat existing, Seat seat) {
        Long existingSeatId = existing.getSeat() != null ? existing.getSeat().getId() : null;
        if (existingSeatId != null && existingSeatId.equals(seat.getId())) {
            return existing.getPrice();
        }
        return seat.getPrice();
    }

    @Override
    public BookingSeatResponseDto getById(Long id) {
        BookingSeat bookingSeat = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        return bookingSeatMapper.toResponseDto(bookingSeat);
    }

    @Override
    public List<BookingSeatResponseDto> getAll() {
        return bookingSeatRepository.findAll().stream()
                .map(bookingSeatMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if (!bookingSeatRepository.existsById(id)) {
            throw new ResourceNotFoundException("BookingSeat", id);
        }
        bookingSeatRepository.deleteById(id);
    }
}