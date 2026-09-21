package com.cinema.booking.dto.orders;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItemResponseDto {

    private Long id;

    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal unitPrice;
    private BigDecimal originalUnitPrice;
    private BigDecimal membershipDiscountAmount;
    private String membershipBenefitCode;
    private Long orderId;
    private Long productId;
    private java.util.UUID userMembershipId;
}
