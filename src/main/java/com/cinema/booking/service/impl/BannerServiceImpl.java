package com.cinema.booking.service.impl;

import com.cinema.booking.dto.banners.BannerAdminResponseDto;
import com.cinema.booking.dto.banners.BannerReorderRequestDto;
import com.cinema.booking.dto.banners.BannerRequestDto;
import com.cinema.booking.dto.banners.BannerResponseDto;
import com.cinema.booking.entity.Banner;
import com.cinema.booking.enums.BannerSection;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.repository.BannerRepository;
import com.cinema.booking.service.BannerService;
import com.cinema.booking.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BannerServiceImpl implements BannerService {

    private static final String CLOUDINARY_BANNER_FOLDER = "Cinema_Project/banner";
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final BannerRepository bannerRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponseDto> getActiveBannersBySection(BannerSection section) {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<Banner> banners = bannerRepository.findActiveBySection(section, now);
        return banners.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerAdminResponseDto> getAllBanners(BannerSection section) {
        List<Banner> banners;
        if (section != null) {
            banners = bannerRepository.findBySectionOrderBySortOrderAscCreatedAtDesc(section);
        } else {
            banners = bannerRepository.findAllByOrderBySectionAscSortOrderAscCreatedAtDesc();
        }
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        return banners.stream()
                .map(b -> toAdminResponseDto(b, now))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BannerAdminResponseDto getBannerById(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", id));
        return toAdminResponseDto(banner, LocalDateTime.now(APP_ZONE));
    }

    @Override
    public BannerAdminResponseDto createBanner(BannerRequestDto requestDto, MultipartFile imageFile) {
        validateDates(requestDto.getStartDate(), requestDto.getEndDate());

        String imageUrl = null;
        String imagePublicId = null;

        if (imageFile != null && !imageFile.isEmpty()) {
            Map<String, Object> uploadResult = cloudinaryService.upload(imageFile, CLOUDINARY_BANNER_FOLDER);
            imageUrl = (String) uploadResult.get("secure_url");
            imagePublicId = (String) uploadResult.get("public_id");
        } else if (requestDto.getImageUrl() != null && !requestDto.getImageUrl().trim().isEmpty()) {
            imageUrl = requestDto.getImageUrl().trim();
        } else {
            throw new IllegalArgumentException("Banner image is required (upload an image file or provide an image URL).");
        }

        Integer sortOrder = requestDto.getSortOrder();
        if (sortOrder == null) {
            Integer maxOrder = bannerRepository.findMaxSortOrderBySection(requestDto.getSection());
            sortOrder = (maxOrder == null || maxOrder < 0) ? 0 : maxOrder + 1;
        }

        Banner banner = Banner.builder()
                .section(requestDto.getSection())
                .title(requestDto.getTitle().trim())
                .subtitle(requestDto.getSubtitle() != null ? requestDto.getSubtitle().trim() : null)
                .imageUrl(imageUrl)
                .imagePublicId(imagePublicId)
                .linkUrl(requestDto.getLinkUrl() != null ? requestDto.getLinkUrl().trim() : null)
                .sortOrder(sortOrder)
                .isActive(requestDto.getIsActive() != null ? requestDto.getIsActive() : true)
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .build();

        Banner saved = bannerRepository.save(banner);
        log.info("Created new banner with id={}, section={}, title={}", saved.getId(), saved.getSection(), saved.getTitle());
        return toAdminResponseDto(saved, LocalDateTime.now(APP_ZONE));
    }

    @Override
    public BannerAdminResponseDto updateBanner(Long id, BannerRequestDto requestDto, MultipartFile imageFile) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", id));

        validateDates(requestDto.getStartDate(), requestDto.getEndDate());

        if (imageFile != null && !imageFile.isEmpty()) {
            // Delete old Cloudinary asset if available
            if (banner.getImagePublicId() != null) {
                try {
                    cloudinaryService.delete(banner.getImagePublicId());
                } catch (Exception e) {
                    log.warn("Failed to delete previous Cloudinary image publicId={}: {}", banner.getImagePublicId(), e.getMessage());
                }
            }
            Map<String, Object> uploadResult = cloudinaryService.upload(imageFile, CLOUDINARY_BANNER_FOLDER);
            banner.setImageUrl((String) uploadResult.get("secure_url"));
            banner.setImagePublicId((String) uploadResult.get("public_id"));
        } else if (requestDto.getImageUrl() != null && !requestDto.getImageUrl().trim().isEmpty()) {
            String newUrl = requestDto.getImageUrl().trim();
            if (!newUrl.equals(banner.getImageUrl())) {
                if (banner.getImagePublicId() != null) {
                    try {
                        cloudinaryService.delete(banner.getImagePublicId());
                    } catch (Exception e) {
                        log.warn("Failed to delete replaced Cloudinary image publicId={}: {}", banner.getImagePublicId(), e.getMessage());
                    }
                    banner.setImagePublicId(null);
                }
                banner.setImageUrl(newUrl);
            }
        }

        if (requestDto.getSection() != null) {
            banner.setSection(requestDto.getSection());
        }
        if (requestDto.getTitle() != null) {
            banner.setTitle(requestDto.getTitle().trim());
        }
        banner.setSubtitle(requestDto.getSubtitle() != null ? requestDto.getSubtitle().trim() : null);
        banner.setLinkUrl(requestDto.getLinkUrl() != null ? requestDto.getLinkUrl().trim() : null);
        if (requestDto.getSortOrder() != null) {
            banner.setSortOrder(requestDto.getSortOrder());
        }
        if (requestDto.getIsActive() != null) {
            banner.setIsActive(requestDto.getIsActive());
        }
        banner.setStartDate(requestDto.getStartDate());
        banner.setEndDate(requestDto.getEndDate());

        Banner updated = bannerRepository.save(banner);
        log.info("Updated banner with id={}, section={}", updated.getId(), updated.getSection());
        return toAdminResponseDto(updated, LocalDateTime.now(APP_ZONE));
    }

    @Override
    public BannerAdminResponseDto toggleBannerStatus(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", id));
        banner.setIsActive(!Boolean.TRUE.equals(banner.getIsActive()));
        Banner saved = bannerRepository.save(banner);
        log.info("Toggled active status for banner id={} to {}", saved.getId(), saved.getIsActive());
        return toAdminResponseDto(saved, LocalDateTime.now(APP_ZONE));
    }

    @Override
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", id));

        if (banner.getImagePublicId() != null) {
            try {
                cloudinaryService.delete(banner.getImagePublicId());
            } catch (Exception e) {
                log.warn("Failed to delete Cloudinary image publicId={}: {}", banner.getImagePublicId(), e.getMessage());
            }
        }

        bannerRepository.delete(banner);
        log.info("Deleted banner with id={}", id);
    }

    @Override
    public List<BannerAdminResponseDto> reorderBanners(BannerReorderRequestDto requestDto) {
        List<Long> bannerIds = requestDto.getBannerIds();
        if (bannerIds == null || bannerIds.isEmpty()) {
            return getAllBanners(requestDto.getSection());
        }

        for (int i = 0; i < bannerIds.size(); i++) {
            Long bannerId = bannerIds.get(i);
            Banner banner = bannerRepository.findById(bannerId).orElse(null);
            if (banner != null && (requestDto.getSection() == null || banner.getSection() == requestDto.getSection())) {
                banner.setSortOrder(i);
                bannerRepository.save(banner);
            }
        }

        log.info("Reordered {} banners for section={}", bannerIds.size(), requestDto.getSection());
        return getAllBanners(requestDto.getSection());
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
    }

    private BannerResponseDto toResponseDto(Banner banner) {
        return BannerResponseDto.builder()
                .id(banner.getId())
                .section(banner.getSection())
                .title(banner.getTitle())
                .subtitle(banner.getSubtitle())
                .imageUrl(banner.getImageUrl())
                .linkUrl(banner.getLinkUrl())
                .sortOrder(banner.getSortOrder())
                .build();
    }

    private BannerAdminResponseDto toAdminResponseDto(Banner banner, LocalDateTime now) {
        String status;
        if (Boolean.FALSE.equals(banner.getIsActive())) {
            status = "DISABLED";
        } else if (banner.getStartDate() != null && now.isBefore(banner.getStartDate())) {
            status = "SCHEDULED";
        } else if (banner.getEndDate() != null && now.isAfter(banner.getEndDate())) {
            status = "EXPIRED";
        } else {
            status = "ACTIVE";
        }

        return BannerAdminResponseDto.builder()
                .id(banner.getId())
                .section(banner.getSection())
                .title(banner.getTitle())
                .subtitle(banner.getSubtitle())
                .imageUrl(banner.getImageUrl())
                .imagePublicId(banner.getImagePublicId())
                .linkUrl(banner.getLinkUrl())
                .sortOrder(banner.getSortOrder())
                .isActive(banner.getIsActive())
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .status(status)
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
