package com.cinema.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "bakong_request_budgets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BakongRequestBudget {

    private static final ZoneId PHNOM_PENH_ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Id
    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "rate_limited_until")
    private LocalDateTime rateLimitedUntil;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now(PHNOM_PENH_ZONE);
    }
}
