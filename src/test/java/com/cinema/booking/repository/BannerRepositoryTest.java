package com.cinema.booking.repository;

import com.cinema.booking.entity.Banner;
import com.cinema.booking.enums.BannerSection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BannerRepositoryTest {

    @Autowired
    private BannerRepository bannerRepository;

    @Test
    void findActiveBySection_filtersInactiveDateBoundedAndOtherSectionsInSortOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 12, 0);

        Banner second = banner("Second active", BannerSection.HOME, 20, true, now.minusDays(1), now.plusDays(1));
        Banner first = banner("First active", BannerSection.HOME, 10, true, null, null);
        Banner inactive = banner("Inactive", BannerSection.HOME, 0, false, null, null);
        Banner future = banner("Future", BannerSection.HOME, 1, true, now.plusHours(1), null);
        Banner expired = banner("Expired", BannerSection.HOME, 2, true, null, now.minusMinutes(1));
        Banner otherSection = banner("Cinema active", BannerSection.CINEMA, 0, true, null, null);

        bannerRepository.saveAll(List.of(second, first, inactive, future, expired, otherSection));
        bannerRepository.flush();

        List<Banner> results = bannerRepository.findActiveBySection(BannerSection.HOME, now);

        assertThat(results)
                .extracting(Banner::getTitle)
                .containsExactly("First active", "Second active");
    }

    private Banner banner(
            String title,
            BannerSection section,
            int sortOrder,
            boolean isActive,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return Banner.builder()
                .section(section)
                .title(title)
                .imageUrl("https://example.test/" + title.toLowerCase().replace(' ', '-') + ".jpg")
                .sortOrder(sortOrder)
                .isActive(isActive)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
