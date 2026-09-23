package com.cinema.booking.entity;

import com.cinema.booking.enums.PromotionDiscountType;
import com.cinema.booking.enums.PromotionScope;
import com.cinema.booking.enums.PromotionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "promotions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_promotions_code", columnNames = "code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private PromotionDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "usage_limit_total")
    private Integer usageLimitTotal;

    @Column(name = "usage_limit_per_user")
    private Integer usageLimitPerUser;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "combinable_with_membership", nullable = false)
    private Boolean combinableWithMembership = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PromotionStatus status = PromotionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private PromotionScope scope = PromotionScope.ALL;

    public PromotionStatus getEffectiveStatus() {
        if (Boolean.FALSE.equals(this.active) || this.status == PromotionStatus.DISABLED || this.status == PromotionStatus.PAUSED) {
            return PromotionStatus.DISABLED;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        if (startDate != null && now.isBefore(startDate)) {
            return PromotionStatus.SCHEDULED;
        }
        if (endDate != null && now.isAfter(endDate)) {
            return PromotionStatus.EXPIRED;
        }
        if (usageLimitTotal != null && usedCount != null && usedCount >= usageLimitTotal) {
            return PromotionStatus.EXHAUSTED;
        }
        return PromotionStatus.ACTIVE;
    }

    @Column(name = "target_movie_id")
    private Long targetMovieId;

    @Column(name = "target_show_id")
    private Long targetShowId;

    @Column(name = "target_product_id")
    private Long targetProductId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        createdAt = now;
        updatedAt = now;
        if (usedCount == null) {
            usedCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
    }
}
