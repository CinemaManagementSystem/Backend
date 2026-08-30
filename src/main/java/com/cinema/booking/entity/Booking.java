package com.cinema.booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.cinema.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @Column(name = "booking_code", nullable = false, unique = true)
    private String bookingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    public void transitionTo(BookingStatus newStatus) {
        if (this.status == newStatus) return;
        
        if (this.status == BookingStatus.PENDING && (newStatus == BookingStatus.CONFIRMED || newStatus == BookingStatus.CANCELLED)) {
            this.status = newStatus;
        } else if (this.status == BookingStatus.CONFIRMED && (newStatus == BookingStatus.COMPLETED || newStatus == BookingStatus.CANCELLED)) {
            this.status = newStatus;
        } else {
            throw new IllegalStateException("Invalid status transition from " + this.status + " to " + newStatus);
        }
    }
}