package com.cinema.booking.dto.promotions;

import com.cinema.booking.enums.PromotionDiscountType;
import com.cinema.booking.enums.PromotionScope;
import com.cinema.booking.enums.PromotionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponseDto {

    private Long id;
    private String name;
    private String description;
    private String code;
    private PromotionDiscountType discountType;

    @JsonProperty("value")
    private BigDecimal value;

    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;

    @JsonProperty("startAt")
    private LocalDateTime startAt;

    @JsonProperty("endAt")
    private LocalDateTime endAt;

    @JsonProperty("usageLimit")
    private Integer usageLimit;

    @JsonProperty("perUserLimit")
    private Integer perUserLimit;

    @Builder.Default
    private Integer usedCount = 0;

    private PromotionScope scope;

    @Builder.Default
    private List<Long> targetIds = new ArrayList<>();

    private Long targetMovieId;
    private Long targetShowId;
    private Long targetProductId;

    private PromotionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Backward-compatibility getters
    public BigDecimal getDiscountValue() {
        return value;
    }

    public LocalDateTime getStartDate() {
        return startAt;
    }

    public LocalDateTime getEndDate() {
        return endAt;
    }

    public Integer getUsageLimitTotal() {
        return usageLimit;
    }

    public Integer getUsageLimitPerUser() {
        return perUserLimit;
    }
}
