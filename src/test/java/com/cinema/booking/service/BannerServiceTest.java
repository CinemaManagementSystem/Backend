package com.cinema.booking.service;

import com.cinema.booking.dto.banners.BannerAdminResponseDto;
import com.cinema.booking.dto.banners.BannerReorderRequestDto;
import com.cinema.booking.dto.banners.BannerRequestDto;
import com.cinema.booking.dto.banners.BannerResponseDto;
import com.cinema.booking.entity.Banner;
import com.cinema.booking.enums.BannerSection;
import com.cinema.booking.repository.BannerRepository;
import com.cinema.booking.service.impl.BannerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock
    private BannerRepository bannerRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    private BannerService bannerService;

    @BeforeEach
    void setUp() {
        bannerService = new BannerServiceImpl(bannerRepository, cloudinaryService);
    }

    @Test
    void getActiveBannersBySection_returnsOnlyActiveBanners() {
        Banner banner1 = Banner.builder()
                .id(1L)
                .section(BannerSection.HOME)
                .title("Home Banner 1")
                .imageUrl("https://example.com/banner1.jpg")
                .sortOrder(0)
                .isActive(true)
                .build();

        Banner banner2 = Banner.builder()
                .id(2L)
                .section(BannerSection.HOME)
                .title("Home Banner 2")
                .imageUrl("https://example.com/banner2.jpg")
                .sortOrder(1)
                .isActive(true)
                .build();

        when(bannerRepository.findActiveBySection(eq(BannerSection.HOME), any(LocalDateTime.class)))
                .thenReturn(List.of(banner1, banner2));

        List<BannerResponseDto> results = bannerService.getActiveBannersBySection(BannerSection.HOME);

        assertEquals(2, results.size());
        assertEquals("Home Banner 1", results.get(0).getTitle());
        assertEquals("Home Banner 2", results.get(1).getTitle());
        verify(bannerRepository).findActiveBySection(eq(BannerSection.HOME), any(LocalDateTime.class));
    }

    @Test
    void createBanner_withImageUrl_assignsNextSortOrderWhenNull() {
        BannerRequestDto request = BannerRequestDto.builder()
                .section(BannerSection.CINEMA)
                .title("New Cinema Banner")
                .imageUrl("https://example.com/cinema.jpg")
                .build();

        when(bannerRepository.findMaxSortOrderBySection(BannerSection.CINEMA)).thenReturn(2);

        Banner savedBanner = Banner.builder()
                .id(10L)
                .section(BannerSection.CINEMA)
                .title("New Cinema Banner")
                .imageUrl("https://example.com/cinema.jpg")
                .sortOrder(3)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(bannerRepository.save(any(Banner.class))).thenReturn(savedBanner);

        BannerAdminResponseDto response = bannerService.createBanner(request, null);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("New Cinema Banner", response.getTitle());
        assertEquals(3, response.getSortOrder());
        assertEquals("ACTIVE", response.getStatus());

        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getSortOrder());
    }

    @Test
    void reorderBanners_updatesSortOrderSequentially() {
        Banner b1 = Banner.builder().id(10L).section(BannerSection.HOME).sortOrder(5).build();
        Banner b2 = Banner.builder().id(20L).section(BannerSection.HOME).sortOrder(1).build();

        when(bannerRepository.findById(10L)).thenReturn(Optional.of(b1));
        when(bannerRepository.findById(20L)).thenReturn(Optional.of(b2));
        when(bannerRepository.findBySectionOrderBySortOrderAscCreatedAtDesc(BannerSection.HOME))
                .thenReturn(List.of(b2, b1));

        BannerReorderRequestDto request = BannerReorderRequestDto.builder()
                .section(BannerSection.HOME)
                .bannerIds(List.of(20L, 10L))
                .build();

        bannerService.reorderBanners(request);

        assertEquals(0, b2.getSortOrder());
        assertEquals(1, b1.getSortOrder());
        verify(bannerRepository).save(b2);
        verify(bannerRepository).save(b1);
    }

    @Test
    void toggleBannerStatus_invertsIsActive() {
        Banner banner = Banner.builder()
                .id(5L)
                .section(BannerSection.FNB)
                .title("FNB Promo")
                .imageUrl("https://example.com/fnb.jpg")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(bannerRepository.findById(5L)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BannerAdminResponseDto response = bannerService.toggleBannerStatus(5L);

        assertFalse(response.getIsActive());
        assertEquals("DISABLED", response.getStatus());
    }

    @Test
    void deleteBanner_removesCloudinaryImageIfPublicIdPresent() {
        Banner banner = Banner.builder()
                .id(7L)
                .section(BannerSection.OFFER)
                .title("Offer Banner")
                .imageUrl("https://res.cloudinary.com/demo/image/upload/v1/Cinema_Project/banner/sample.jpg")
                .imagePublicId("Cinema_Project/banner/sample")
                .build();

        when(bannerRepository.findById(7L)).thenReturn(Optional.of(banner));

        bannerService.deleteBanner(7L);

        verify(cloudinaryService).delete("Cinema_Project/banner/sample");
        verify(bannerRepository).delete(banner);
    }
}
