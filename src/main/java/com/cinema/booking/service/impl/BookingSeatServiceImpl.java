package com.cinema.booking.service.impl;

import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.Seat;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.dto.bookings.BookingSeatRequestDto;
import com.cinema.booking.dto.bookings.BookingSeatResponseDto;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.exception.SeatUnavailableException;
import com.cinema.booking.mapper.BookingSeatMapper;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.SeatRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BookingSeatService;
import com.cinema.booking.service.BookingTotalService;
import com.cinema.booking.service.BookingService;
import com.cinema.booking.service.MembershipPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingSeatServiceImpl implements BookingSeatService {

    // PENDING is the existing persisted name for the HELD state. CONFIRMED is
    // BOOKED and CANCELLED is RELEASED; keeping these names preserves clients.
    private static final String DEFAULT_STATUS = "PENDING";

    private final BookingSeatRepository bookingSeatRepository;
    private final BookingSeatMapper bookingSeatMapper;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final AuthorizationService authorizationService;
    private final BookingTotalService bookingTotalService;
    private final BookingService bookingService;
    private final MembershipPricingService membershipPricingService;

    @Override
    @Transactional
    public BookingSeatResponseDto create(BookingSeatRequestDto dto) {
        Booking booking = bookingRepository.findByIdForUpdate(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        ensureBookingCanSelectSeats(booking);
        ensureBookingHoldActive(booking);
        Seat seat = seatRepository.findByIdForUpdate(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));
        ensureSeatBelongsToShow(booking, seat);
        ensureSeatAvailable(booking, seat, null);

        BookingSeat bookingSeat = bookingSeatMapper.toEntity(dto);
        bookingSeat.setBooking(booking);
        bookingSeat.setSeat(seat);
        bookingSeat.setShowId(booking.getShow().getId());
        applyMembershipTicketSnapshot(bookingSeat, booking.getCustomer(), seat.getPrice());
        bookingSeat.setStatus(DEFAULT_STATUS);
        bookingSeat.setExpiresAt(booking.getExpiresAt());
        bookingSeat = bookingSeatRepository.save(bookingSeat);
        membershipPricingService.recordUsage(
                membershipPricingService.calculateTicketPrice(booking.getCustomer(), seat.getPrice()),
                "BOOKING_SEAT",
                bookingSeat.getId()
        );
        bookingTotalService.recalculate(booking);
        return bookingSeatMapper.toResponseDto(bookingSeat);
    }

    @Override
    @Transactional
    public BookingSeatResponseDto update(Long id, BookingSeatRequestDto dto) {
        BookingSeat existing = bookingSeatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSeat", id));
        authorizationService.requireOwnerOrStaff(existing.getBooking().getCustomer());
        if (!existing.getBooking().getId().equals(dto.getBookingId())) {
            throw new IllegalArgumentException("A booking seat cannot be moved to another booking");
        }
        if (!"PENDING".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("Only pending booking seats can be modified");
        }
        Booking booking = bookingRepository.findByIdForUpdate(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        ensureBookingCanSelectSeats(booking);
        ensureBookingHoldActive(booking);
        if (existing.getSeat() != null && !existing.getSeat().getId().equals(dto.getSeatId())) {
            seatRepository.findByIdForUpdate(existing.getSeat().getId());
        }
        Seat seat = seatRepository.findByIdForUpdate(dto.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", dto.getSeatId()));
        ensureSeatBelongsToShow(booking, seat);
        ensureSeatAvailable(booking, seat, existing.getId());

        BookingSeat updated = bookingSeatMapper.toEntity(dto);
        Booking previousBooking = existing.getBooking();
        updated.setId(existing.getId());
        updated.setBooking(booking);
        updated.setSeat(seat);
        updated.setShowId(booking.getShow().getId());
        if (isSameSeat(existing, booking, seat)) {
            copyPricingSnapshot(existing, updated);
        } else {
            applyMembershipTicketSnapshot(updated, booking.getCustomer(), seat.getPrice());
        }
        updated.setStatus(existing.getStatus());
        updated.setExpiresAt(booking.getExpiresAt());
        updated = bookingSeatRepository.save(updated);
        if (!isSameSeat(existing, booking, seat)) {
            membershipPricingService.recordUsage(
                    membershipPricingService.calculateTicketPrice(booking.getCustomer(), seat.getPrice()),
                    "BOOKING_SEAT",
                    updated.getId()
            );
        }
        bookingTotalService.recalculate(previousBooking);
        if (!previousBooking.getId().equals(booking.getId())) {
            bookingTotalService.recalculate(booking);
        }
        return bookingSeatMapper.toResponseDto(updated);
    }

    private void ensureSeatAvailable(Booking booking, Seat seat, Long excludedBookingSeatId) {
        if (bookingSeatRepository.existsActiveReservationForShowSeat(
                booking.getShow().getId(),
                seat.getId(),
                excludedBookingSeatId
        )) {
            throw new SeatUnavailableException(booking.getShow().getId(), seat.getId());
        }
    }

    private void ensureBookingCanSelectSeats(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Seats can only be selected for a pending booking");
        }
    }

    private void ensureBookingHoldActive(Booking booking) {
        if (booking.getExpiresAt() != null
                && !booking.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")))) {
            // Expire and release the hold before rejecting this request. This
            // closes the scheduler's up-to-one-minute enforcement gap.
            bookingService.expirePendingBooking(booking.getId());
            throw new IllegalStateException("Booking hold has expired");
        }
    }

    private void ensureSeatBelongsToShow(Booking booking, Seat seat) {
        if (booking.getShow() == null || booking.getShow().getScreen() == null
                || seat.getScreen() == null
                || !booking.getShow().getScreen().getId().equals(seat.getScreen().getId())) {
            throw new IllegalArgumentException("Seat does not belong to the selected show's screen");
        }
    }

    private boolean isSameSeat(BookingSeat existing, Booking booking, Seat seat) {
        Long existingSeatId = existing.getSeat() != null ? existing.getSeat().getId() : null;
        Long existingBookingId = existing.getBooking() != null ? existing.getBooking().getId() : null;
        return existingSeatId != null && existingSeatId.equals(seat.getId())
                && existingBookingId != null && existingBookingId.equals(booking.getId());
    }

    private void applyMembershipTicketSnapshot(BookingSeat bookingSeat, com.cinema.booking.entity.User customer, BigDecimal basePrice) {
        MembershipPricingService.PricingResult result = membershipPricingService.calculateTicketPrice(customer, basePrice);
        bookingSeat.setOriginalPrice(result.originalAmount());
        bookingSeat.setMembershipDiscountAmount(result.discountAmount());
        bookingSeat.setMembershipBenefitCode(result.benefitType() != null ? result.benefitType().name() : null);
        bookingSeat.setUserMembership(result.membership());
        bookingSeat.setPrice(result.finalAmount());
    }

    private void copyPricingSnapshot(BookingSeat source, BookingSeat target) {
        target.setOriginalPrice(source.getOriginalPrice() != null ? source.getOriginalPrice() : source.getPrice());
        target.setMembershipDiscountAmount(source.getMembershipDiscountAmount() != null ? source.getMembershipDiscountAmount() : BigDecimal.ZERO);
        target.setMembershipBenefitCode(source.getMembershipBenefitCode());
        target.setUserMembership(source.getUserMembership());
        target.setPrice(source.getPrice());
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
        if (!"PENDING".equalsIgnoreCase(bookingSeat.getStatus())
                || bookingSeat.getBooking().getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending booking seats can be released");
        }
        Booking booking = bookingSeat.getBooking();
        Long bookingId = booking.getId();
        booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (bookingSeat.getSeat() != null) {
            seatRepository.findByIdForUpdate(bookingSeat.getSeat().getId());
        }
        bookingSeatRepository.delete(bookingSeat);
        bookingTotalService.recalculate(booking);
    }
}
