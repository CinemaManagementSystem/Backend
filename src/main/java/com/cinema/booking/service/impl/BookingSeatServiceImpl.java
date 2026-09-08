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
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BookingSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public BookingSeatResponseDto create(BookingSeatRequestDto dto) {
        Booking booking = bookingRepository.findById(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        Seat seat = seatRepository.findByIdForUpdate(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));
        ensureSeatAvailable(booking, seat, null);

        BookingSeat bookingSeat = bookingSeatMapper.toEntity(dto);
        bookingSeat.setBooking(booking);
        bookingSeat.setSeat(seat);
        bookingSeat.setPrice(seat.getPrice());
        bookingSeat.setStatus(DEFAULT_STATUS);
        bookingSeat = bookingSeatRepository.save(bookingSeat);
        return bookingSeatMapper.toResponseDto(bookingSeat);
    }

    @Override
    @Transactional
    public BookingSeatResponseDto update(Long id, BookingSeatRequestDto dto) {
        BookingSeat existing = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        authorizationService.requireOwnerOrStaff(existing.getBooking().getCustomer());
        Booking booking = bookingRepository.findById(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        Seat seat = seatRepository.findByIdForUpdate(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));
        ensureSeatAvailable(booking, seat, existing.getId());

        BookingSeat updated = bookingSeatMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setBooking(booking);
        updated.setSeat(seat);
        updated.setPrice(resolvePrice(existing, seat));
        updated.setStatus(existing.getStatus());
        updated = bookingSeatRepository.save(updated);
        return bookingSeatMapper.toResponseDto(updated);
    }

    private void ensureSeatAvailable(Booking booking, Seat seat, Long excludedBookingSeatId) {
        if (bookingSeatRepository.existsActiveReservationForShowSeat(
                booking.getShow().getId(),
                seat.getId(),
                excludedBookingSeatId
        )) {
            throw new IllegalStateException("Seat is already reserved for this show");
        }
    }

    private BigDecimal resolvePrice(BookingSeat existing, Seat seat) {
        Long existingSeatId = existing.getSeat() != null ? existing.getSeat().getId() : null;
        if (existingSeatId != null && existingSeatId.equals(seat.getId())) {
            return existing.getPrice();
        }
        return seat.getPrice();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSeatResponseDto getById(Long id) {
        BookingSeat bookingSeat = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        authorizationService.requireOwnerOrStaff(bookingSeat.getBooking().getCustomer());
        return bookingSeatMapper.toResponseDto(bookingSeat);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingSeatResponseDto> getAll() {
        var currentUser = authorizationService.getCurrentUser();
        List<BookingSeat> bookingSeats = authorizationService.isStaffOrAdmin(currentUser)
                ? bookingSeatRepository.findAll()
                : bookingSeatRepository.findByBookingCustomerId(currentUser.getId());
        return bookingSeats.stream()
                .map(bookingSeatMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BookingSeat bookingSeat = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        authorizationService.requireOwnerOrStaff(bookingSeat.getBooking().getCustomer());
        bookingSeatRepository.delete(bookingSeat);
    }
}
