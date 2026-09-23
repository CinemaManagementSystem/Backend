package com.cinema.booking.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PromotionStatus {
    ACTIVE,
    SCHEDULED,
    EXPIRED,
    DISABLED,
    EXHAUSTED,
    // Legacy / Admin Draft statuses
    DRAFT,
    PAUSED;

    @JsonCreator
    public static PromotionStatus fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        if ("PAUSED".equals(normalized) || "INACTIVE".equals(normalized)) {
            return DISABLED;
        }
        return PromotionStatus.valueOf(normalized);
    }
}
