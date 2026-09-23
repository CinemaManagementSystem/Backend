package com.cinema.booking.dto.promotions;

import com.cinema.booking.enums.PromotionDiscountType;
import com.cinema.booking.enums.PromotionScope;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PromotionRequestDto {

    @NotBlank(message = "Promotion name is required")
    private String name;

    private String description;

    private String code;

    @NotNull(message = "Discount type is required")
    private PromotionDiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
    @JsonAlias({"discountValue", "value"})
    private BigDecimal value;

    private BigDecimal maxDiscountAmount;

    private BigDecimal minOrderAmount;

    @NotNull(message = "Start date is required")
    @JsonAlias({"startDate", "startAt"})
    private String startAt;

    @NotNull(message = "End date is required")
    @JsonAlias({"endDate", "endAt"})
    private String endAt;

    @JsonAlias({"usageLimitTotal", "usageLimit"})
    private Integer usageLimit;

    @JsonAlias({"usageLimitPerUser", "perUserLimit"})
    private Integer perUserLimit;

    @NotNull(message = "Scope is required")
    private PromotionScope scope = PromotionScope.ALL;

    private List<Object> targetIds = new ArrayList<>();

    private Long targetMovieId;
    private Long targetShowId;
    private Long targetProductId;

    // Helper getters for backward compatibility
    public BigDecimal getDiscountValue() {
        return value;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.value = discountValue;
    }

    public String getStartDate() {
        return startAt;
    }

    public void setStartDate(String startDate) {
        this.startAt = startDate;
    }

    public String getEndDate() {
        return endAt;
    }

    public void setEndDate(String endDate) {
        this.endAt = endDate;
    }

    public Integer getUsageLimitTotal() {
        return usageLimit;
    }

    public void setUsageLimitTotal(Integer usageLimitTotal) {
        this.usageLimit = usageLimitTotal;
    }

    public Integer getUsageLimitPerUser() {
        return perUserLimit;
    }

    public void setUsageLimitPerUser(Integer usageLimitPerUser) {
        this.perUserLimit = usageLimitPerUser;
    }
}
