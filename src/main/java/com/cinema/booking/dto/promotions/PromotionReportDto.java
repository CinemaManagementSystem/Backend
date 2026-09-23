package com.cinema.booking.dto.promotions;

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
public class PromotionReportDto {

    @Builder.Default
    private PromotionReportSummaryDto summary = new PromotionReportSummaryDto();

    @Builder.Default
    private List<PromotionUsageDto> usages = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionReportSummaryDto {
        @Builder.Default
        private long totalUses = 0;

        @Builder.Default
        private BigDecimal totalDiscountGiven = BigDecimal.ZERO;

        @Builder.Default
        private BigDecimal revenueFromPromoOrders = BigDecimal.ZERO;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionUsageDto {
        private String id;
        private String promotionId;
        private String userId;
        private String userName;
        private String orderId;
        private BigDecimal discountApplied;
        private LocalDateTime usedAt;
    }
}
