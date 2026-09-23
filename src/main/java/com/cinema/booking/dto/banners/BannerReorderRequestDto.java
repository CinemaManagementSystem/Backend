package com.cinema.booking.dto.banners;

import com.cinema.booking.enums.BannerSection;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerReorderRequestDto {

    @NotEmpty(message = "Banner IDs list cannot be empty")
    private List<Long> bannerIds;

    private BannerSection section;
}
