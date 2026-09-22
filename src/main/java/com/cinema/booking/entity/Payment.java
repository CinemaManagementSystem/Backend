package com.cinema.booking.entity;

import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = true)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    // Nullable: only set for gateway/KHQR payments; null for CASH
    @Column(name = "transaction_id", nullable = true, unique = true)
    private String transactionId;

    // KHQR-specific fields (null for CASH payments)
    @Column(name = "khqr_string", nullable = true, columnDefinition = "TEXT")
    private String khqrString;

    @Column(name = "md5_hash", nullable = true)
    private String md5Hash;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Column(name = "verification_started_at")
    private LocalDateTime verificationStartedAt;

    @Column(name = "last_verification_at")
    private LocalDateTime lastVerificationAt;

    @Column(name = "next_verification_at")
    private LocalDateTime nextVerificationAt;

    @Column(name = "verification_attempt_count", nullable = false)
    private int verificationAttemptCount;

    @Column(name = "scheduled_verification_count", nullable = false)
    private int scheduledVerificationCount;

    @Column(name = "manual_verification_count", nullable = false)
    private int manualVerificationCount;

    @Column(name = "verification_failure_count", nullable = false)
    private int verificationFailureCount;

    @Column(name = "last_verification_error", length = 500)
    private String lastVerificationError;

    @Column(name = "rate_limited_until")
    private LocalDateTime rateLimitedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_membership_id", nullable = true)
    private UserMembership userMembership;

}
