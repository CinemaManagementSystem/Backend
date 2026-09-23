package com.cinema.booking.repository;

import com.cinema.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    List<BookingSeat> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bs from BookingSeat bs where bs.booking.id = :bookingId order by bs.id")
    List<BookingSeat> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    List<BookingSeat> findByBookingCustomerId(Long customerId);

    @Query("""
            select count(bs) > 0
            from BookingSeat bs
            where bs.showId = :showId
              and bs.seat.id = :seatId
              and (:excludedBookingSeatId is null or bs.id <> :excludedBookingSeatId)
              and upper(bs.status) not in ('CANCELLED', 'EXPIRED', 'RELEASED')
              and bs.booking.status not in (
                  com.cinema.booking.enums.BookingStatus.CANCELLED,
                  com.cinema.booking.enums.BookingStatus.EXPIRED
              )
            """)
    boolean existsActiveReservationForShowSeat(
            @Param("showId") Long showId,
            @Param("seatId") Long seatId,
            @Param("excludedBookingSeatId") Long excludedBookingSeatId
    );

    @Query("""
            select count(bs) > 0
            from BookingSeat bs
            where bs.seat.id = :seatId
              and upper(bs.status) not in ('CANCELLED', 'EXPIRED', 'RELEASED')
              and bs.booking.status not in (
                  com.cinema.booking.enums.BookingStatus.CANCELLED,
                  com.cinema.booking.enums.BookingStatus.EXPIRED
              )
            """)
    boolean existsActiveReservationBySeatId(@Param("seatId") Long seatId);

    @Query("""
            select coalesce(sum(bs.price), 0)
            from BookingSeat bs
            where bs.booking.id = :bookingId
              and upper(bs.status) not in ('CANCELLED', 'EXPIRED', 'RELEASED')
            """)
    BigDecimal sumActivePricesByBookingId(@Param("bookingId") Long bookingId);
}
