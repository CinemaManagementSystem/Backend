package com.cinema.booking.repository;

import com.cinema.booking.entity.Booking;
import com.cinema.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerId(Long customerId);

    Optional<Booking> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    boolean existsByShowId(Long showId);

    boolean existsByShowIdAndStatusIn(Long showId, Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            where b.status = :status
              and ((b.expiresAt is not null and b.expiresAt <= :now)
                   or (b.expiresAt is null and b.show.startTime <= :showCutoff))
            """)
    List<Booking> findPendingBookingsPastHoldDeadline(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now,
            @Param("showCutoff") LocalDateTime showCutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
