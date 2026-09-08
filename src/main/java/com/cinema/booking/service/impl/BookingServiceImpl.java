package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.Show;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.BookingMapper;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final ShowRepository showRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public BookingResponseDto create(BookingRequestDto dto) {
        Booking booking = bookingMapper.toEntity(dto);
        User customer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId());
        booking.setCustomer(customer);
        Show show = showRepository.findById(dto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId()));
        booking.setShow(show);
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);
        return bookingMapper.toResponseDto(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto update(Long id, BookingRequestDto dto) {
        Booking existing = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        authorizationService.requireOwnerOrStaff(existing.getCustomer());

        Booking updated = bookingMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setCustomer(authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId()));
        updated.setShow(showRepository.findById(dto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId())));
        updated = bookingRepository.save(updated);
        return bookingMapper.toResponseDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        return bookingMapper.toResponseDto(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getAll() {
        User currentUser = authorizationService.getCurrentUser();
        List<Booking> bookings = authorizationService.isStaffOrAdmin(currentUser)
                ? bookingRepository.findAll()
                : bookingRepository.findByCustomerId(currentUser.getId());
        return bookings.stream()
                .map(bookingMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        bookingRepository.delete(booking);
    }
}
