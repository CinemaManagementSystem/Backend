package com.cinema.booking.repository;

import com.cinema.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    List<BookingSeat> findByBookingId(Long bookingId);

    List<BookingSeat> findByBookingCustomerId(Long customerId);

    @Query("""
            select count(bs) > 0
            from BookingSeat bs
            where bs.booking.show.id = :showId
              and bs.seat.id = :seatId
              and (:excludedBookingSeatId is null or bs.id <> :excludedBookingSeatId)
              and upper(bs.status) <> 'CANCELLED'
            """)
    boolean existsActiveReservationForShowSeat(
            @Param("showId") Long showId,
            @Param("seatId") Long seatId,
            @Param("excludedBookingSeatId") Long excludedBookingSeatId
    );
}
