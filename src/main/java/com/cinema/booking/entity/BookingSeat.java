package com.cinema.booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "original_price")
    private BigDecimal originalPrice;

    @Column(name = "membership_discount_amount")
    private BigDecimal membershipDiscountAmount = BigDecimal.ZERO;

    @Column(name = "membership_benefit_code")
    private String membershipBenefitCode;

    @Column(name = "status", nullable = false)
    private String status;

    /** Copied from the parent booking so clients can render the same deadline. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Denormalized from booking.show. PostgreSQL uses this value in the
     * partial unique index that makes an active seat hold unique per show.
     */
    @Column(name = "show_id")
    private Long showId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_membership_id")
    private UserMembership userMembership;

}
