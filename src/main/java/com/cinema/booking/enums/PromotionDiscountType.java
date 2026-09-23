package com.cinema.booking.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PromotionDiscountType {
    PERCENT,
    FIXED_AMOUNT;

    @JsonCreator
    public static PromotionDiscountType fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        if ("FIXED".equals(normalized) || "FIXED_AMOUNT".equals(normalized)) {
            return FIXED_AMOUNT;
        }
        if ("PERCENT".equals(normalized) || "PERCENTAGE".equals(normalized)) {
            return PERCENT;
        }
        return PromotionDiscountType.valueOf(normalized);
    }

    @JsonValue
    public String toValue() {
        return this == FIXED_AMOUNT ? "FIXED_AMOUNT" : "PERCENTAGE";
    }
}
