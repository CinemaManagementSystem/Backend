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
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final UserRepository userRepository;
    private final ShowRepository showRepository;

    @Override
    public BookingResponseDto create(BookingRequestDto dto) {
        Booking booking = bookingMapper.toEntity(dto);
        User customer = userRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getCustomerId()));
        booking.setCustomer(customer);
        Show show = showRepository.findById(dto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId()));
        booking.setShow(show);
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);
        return bookingMapper.toResponseDto(booking);
    }

    @Override
    public BookingResponseDto update(Long id, BookingRequestDto dto) {
        Booking existing = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        Booking updated = bookingMapper.toEntity(dto);
        updated.setId(existing.getId());
        updated.setCustomer(userRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getCustomerId())));
        updated.setShow(showRepository.findById(dto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId())));
        updated = bookingRepository.save(updated);
        return bookingMapper.toResponseDto(updated);
    }

    @Override
    public BookingResponseDto getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        return bookingMapper.toResponseDto(booking);
    }

    @Override
    public List<BookingResponseDto> getAll() {
        return bookingRepository.findAll().stream()
                .map(bookingMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Booking", id);
        }
        bookingRepository.deleteById(id);
    }
}