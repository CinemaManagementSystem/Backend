package com.cinema.booking.dto.banners;

import com.cinema.booking.enums.BannerSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerAdminResponseDto {
    private Long id;
    private BannerSection section;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String imagePublicId;
    private String linkUrl;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status; // ACTIVE, SCHEDULED, EXPIRED, DISABLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
