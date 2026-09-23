package com.cinema.booking.service;

import com.cinema.booking.dto.banners.BannerAdminResponseDto;
import com.cinema.booking.dto.banners.BannerReorderRequestDto;
import com.cinema.booking.dto.banners.BannerRequestDto;
import com.cinema.booking.dto.banners.BannerResponseDto;
import com.cinema.booking.enums.BannerSection;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BannerService {

    List<BannerResponseDto> getActiveBannersBySection(BannerSection section);

    List<BannerAdminResponseDto> getAllBanners(BannerSection section);

    BannerAdminResponseDto getBannerById(Long id);

    BannerAdminResponseDto createBanner(BannerRequestDto requestDto, MultipartFile imageFile);

    BannerAdminResponseDto updateBanner(Long id, BannerRequestDto requestDto, MultipartFile imageFile);

    BannerAdminResponseDto toggleBannerStatus(Long id);

    void deleteBanner(Long id);

    List<BannerAdminResponseDto> reorderBanners(BannerReorderRequestDto requestDto);
}
