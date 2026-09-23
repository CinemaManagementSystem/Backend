package com.cinema.booking.controller;

import com.cinema.booking.dto.banners.BannerAdminResponseDto;
import com.cinema.booking.dto.banners.BannerReorderRequestDto;
import com.cinema.booking.dto.banners.BannerRequestDto;
import com.cinema.booking.enums.BannerSection;
import com.cinema.booking.service.BannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) BannerSection section) {
        List<BannerAdminResponseDto> banners = bannerService.getAllBanners(section);
        if (section != null) {
            return ResponseEntity.ok(banners);
        }

        Map<BannerSection, List<BannerAdminResponseDto>> grouped = new LinkedHashMap<>();
        for (BannerSection value : BannerSection.values()) {
            grouped.put(value, banners.stream()
                    .filter(banner -> banner.getSection() == value)
                    .toList());
        }
        return ResponseEntity.ok(grouped);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BannerAdminResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.getBannerById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BannerAdminResponseDto> createJson(@Valid @RequestBody BannerRequestDto dto) {
        return new ResponseEntity<>(bannerService.createBanner(dto, null), HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerAdminResponseDto> createMultipart(
            @Valid @ModelAttribute BannerRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        return new ResponseEntity<>(bannerService.createBanner(dto, image), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BannerAdminResponseDto> updateJson(
            @PathVariable Long id,
            @Valid @RequestBody BannerRequestDto dto
    ) {
        return ResponseEntity.ok(bannerService.updateBanner(id, dto, null));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerAdminResponseDto> updateMultipart(
            @PathVariable Long id,
            @Valid @ModelAttribute BannerRequestDto dto,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(bannerService.updateBanner(id, dto, image));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<List<BannerAdminResponseDto>> reorder(@Valid @RequestBody BannerReorderRequestDto dto) {
        return ResponseEntity.ok(bannerService.reorderBanners(dto));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<BannerAdminResponseDto> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.toggleBannerStatus(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
