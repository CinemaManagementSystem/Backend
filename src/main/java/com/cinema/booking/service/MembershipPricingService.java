package com.cinema.booking.service;

import com.cinema.booking.entity.User;
import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.MembershipBenefitType;

import java.math.BigDecimal;

public interface MembershipPricingService {

    PricingResult calculateTicketPrice(User customer, BigDecimal seatPrice);

    PricingResult calculateFoodUnitPrice(User customer, BigDecimal productPrice);

    void recordUsage(PricingResult result, String sourceType, Long sourceId);

    record PricingResult(
            UserMembership membership,
            MembershipBenefitType benefitType,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            boolean shouldRecordUsage
    ) {
        public boolean hasDiscount() {
            return discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
