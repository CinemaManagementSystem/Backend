package com.cinema.booking.dto.banners;

import com.cinema.booking.enums.BannerSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerRequestDto {

    @NotNull(message = "Banner section is required")
    private BannerSection section;

    @NotBlank(message = "Banner title is required")
    @Size(max = 255, message = "Banner title must not exceed 255 characters")
    private String title;

    @Size(max = 500, message = "Subtitle must not exceed 500 characters")
    private String subtitle;

    @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
    private String imageUrl;

    @Size(max = 1000, message = "Link URL must not exceed 1000 characters")
    private String linkUrl;

    private Integer sortOrder;

    private Boolean isActive;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;
}
