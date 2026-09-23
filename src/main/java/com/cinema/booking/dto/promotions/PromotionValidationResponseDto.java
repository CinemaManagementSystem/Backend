package com.cinema.booking.dto.promotions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionValidationResponseDto {

    private boolean valid;
    private BigDecimal discountAmount;
    private String message;
    private String code;
    private String promotionId;
    private BigDecimal total;
}
