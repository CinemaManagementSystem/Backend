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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "membership_benefits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipBenefit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false)
    private MembershipBenefitType benefitType;

    @Column(name = "benefit_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "monthly_limit")
    private Integer monthlyLimit;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
    }
}
