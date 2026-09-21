package com.cinema.booking.entity;

import com.cinema.booking.enums.MembershipBenefitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "membership_usage", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_membership_usage_source",
                columnNames = {"user_membership_id", "benefit_type", "usage_period", "source_type", "source_id"}
        )
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_membership_id", nullable = false)
    private UserMembership userMembership;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false)
    private MembershipBenefitType benefitType;

    @Column(name = "usage_period", nullable = false)
    private String usagePeriod;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        if (usagePeriod == null) {
            usagePeriod = YearMonth.from(createdAt).toString();
        }
    }
}
