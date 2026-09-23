package com.cinema.booking.service.impl;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.Show;
import com.cinema.booking.entity.BookingSeat;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.PaymentTransaction;
import com.cinema.booking.config.BookingHoldConfig;
import com.cinema.booking.dto.bookings.BookingRequestDto;
import com.cinema.booking.dto.bookings.BookingResponseDto;
import com.cinema.booking.enums.BookingStatus;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.exception.IdempotencyKeyConflictException;
import com.cinema.booking.mapper.BookingMapper;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.ShowRepository;
import com.cinema.booking.repository.BookingSeatRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.PaymentTransactionRepository;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final ShowRepository showRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AuthorizationService authorizationService;
    private final BookingHoldConfig bookingHoldConfig;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BookingResponseDto create(BookingRequestDto dto) {
        return create(dto, null);
    }

    @Override
    @Transactional
    public BookingResponseDto create(BookingRequestDto dto, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        User requestedCustomer = authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId());
        // Serialize retries for one customer. The PostgreSQL unique index is
        // still the final authority if another code path bypasses this lock.
        User customer = normalizedKey == null
                ? requestedCustomer
                : userRepository.findByIdForUpdate(requestedCustomer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", requestedCustomer.getId()));
        if (normalizedKey != null) {
            Booking existing = bookingRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), normalizedKey)
                    .orElse(null);
            if (existing != null) {
                if (existing.getShow() == null || !existing.getShow().getId().equals(dto.getShowId())) {
                    throw new IdempotencyKeyConflictException();
                }
                return bookingMapper.toResponseDto(existing);
            }
        }

        Booking booking = bookingMapper.toEntity(dto);
        booking.setCustomer(customer);
        Show show = showRepository.findById(dto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId()));
        ensureShowCanBeBooked(show);
        booking.setShow(show);
        LocalDateTime bookedAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        booking.setBookedAt(bookedAt);
        booking.setExpiresAt(resolveExpiryAt(show));
        booking.setBookingCode("BOOK-" + UUID.randomUUID());
        booking.setIdempotencyKey(normalizedKey);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(BigDecimal.ZERO);
        booking = bookingRepository.save(booking);
        return bookingMapper.toResponseDto(booking);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be 128 characters or fewer");
        }
        return normalized;
    }

    @Override
    @Transactional
    public BookingResponseDto update(Long id, BookingRequestDto dto) {
        Booking existing = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        authorizationService.requireOwnerOrStaff(existing.getCustomer());
        if (existing.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be modified");
        }

        if (!existing.getCustomer().getId().equals(dto.getCustomerId())
                || !existing.getShow().getId().equals(dto.getShowId())) {
            if (!bookingSeatRepository.findByBookingId(existing.getId()).isEmpty()) {
                throw new IllegalStateException("Customer and show cannot change after seats are selected");
            }
            existing.setCustomer(authorizationService.resolveCustomerForAuthenticatedRequest(dto.getCustomerId()));
            Show updatedShow = showRepository.findById(dto.getShowId())
                    .orElseThrow(() -> new ResourceNotFoundException("Show", dto.getShowId()));
            ensureShowCanBeBooked(updatedShow);
            existing.setShow(updatedShow);
            existing.setExpiresAt(resolveExpiryAt(updatedShow));
        }
        existing.setTotalAmount(bookingSeatRepository.sumActivePricesByBookingId(existing.getId()));
        return bookingMapper.toResponseDto(bookingRepository.save(existing));
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
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        authorizationService.requireOwnerOrStaff(booking.getCustomer());
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be cancelled");
        }
        booking.transitionTo(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        releaseSeatsAndSetPaymentStatus(booking, PaymentStatus.FAILED);
    }

    @Override
    @Transactional
    public void expirePendingBooking(Long id) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        if (booking.getStatus() != BookingStatus.PENDING) {
            return;
        }
        booking.transitionTo(BookingStatus.EXPIRED);
        bookingRepository.save(booking);
        releaseSeatsAndSetPaymentStatus(booking, PaymentStatus.EXPIRED);
    }

    private void releaseSeatsAndSetPaymentStatus(Booking booking, PaymentStatus paymentStatus) {
        List<BookingSeat> seats = bookingSeatRepository.findByBookingId(booking.getId());
        seats.forEach(seat -> seat.setStatus("CANCELLED"));
        bookingSeatRepository.saveAll(seats);

        List<Payment> payments = paymentRepository.findByBookingIdAndStatusOrderByIdAsc(
                booking.getId(), PaymentStatus.PENDING);
        payments.forEach(payment -> payment.setStatus(paymentStatus));
        paymentRepository.saveAll(payments);
        for (Payment payment : payments) {
            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByPaymentIdAndStatusOrderByIdAsc(payment.getId(), PaymentStatus.PENDING);
            transactions.forEach(transaction -> transaction.setStatus(paymentStatus));
            paymentTransactionRepository.saveAll(transactions);
        }
    }

    private void ensureShowCanBeBooked(Show show) {
        if (show.getStatus() == null || "CANCELLED".equalsIgnoreCase(show.getStatus())
                || "COMPLETED".equalsIgnoreCase(show.getStatus())) {
            throw new IllegalStateException("Show is not available for booking");
        }
        if (show.getEndTime() != null
                && !show.getEndTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")))) {
            throw new IllegalStateException("Show has already ended");
        }
        LocalDateTime expiryAt = resolveExpiryAt(show);
        if (!expiryAt.isAfter(LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")))) {
            throw new IllegalStateException("Show is no longer available for booking within the final hold period");
        }
    }

    private int resolveHoldTtlMinutes() {
        return bookingHoldConfig.getHoldTtlMinutes() > 0
                ? bookingHoldConfig.getHoldTtlMinutes()
                : 5;
    }

    private LocalDateTime resolveExpiryAt(Show show) {
        return show.getStartTime().minusMinutes(resolveHoldTtlMinutes());
    }
}
