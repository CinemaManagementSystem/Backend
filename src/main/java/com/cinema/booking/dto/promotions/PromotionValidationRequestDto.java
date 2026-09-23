package com.cinema.booking.dto.promotions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionValidationRequestDto {

    private String code;

    @Builder.Default
    private List<CartItemForPromotionDto> cartItems = new ArrayList<>();

    private BigDecimal subtotal;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemForPromotionDto {
        private Object productId;
        private Object movieId;
        private Object showId;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
