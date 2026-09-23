package com.cinema.booking.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PromotionScope {
    ALL,
    TICKET,
    FOOD_AND_BEVERAGE,
    BOOKING,
    // Legacy scopes for backward compatibility
    MOVIE,
    SHOW,
    PRODUCT;

    @JsonCreator
    public static PromotionScope fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        if ("F&B".equals(normalized) || "FOOD".equals(normalized) || "FOOD_AND_DRINKS".equals(normalized)
                || "DRINKS".equals(normalized) || "SNACK".equals(normalized) || "SNACKS".equals(normalized)) {
            return FOOD_AND_BEVERAGE;
        }
        if ("TICKETS".equals(normalized)) {
            return TICKET;
        }
        if ("BOOKINGS".equals(normalized) || "ENTIRE_BOOKING".equals(normalized)) {
            return BOOKING;
        }
        return PromotionScope.valueOf(normalized);
    }
}
