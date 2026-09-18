package com.cinema.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of truth for the pre-show booking expiry buffer.
 *
 * A pending booking expires this many minutes before its show's start
 * time. The booking, its booking seats, its payment, and the scheduler
 * all use the same deadline.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "booking")
public class BookingHoldConfig {
    private int holdTtlMinutes = 5;
    private long expiryRateMs = 60_000L;
}
