package com.cinema.booking.controller;

import com.cinema.booking.dto.banners.BannerResponseDto;
import com.cinema.booking.enums.BannerSection;
import com.cinema.booking.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/{section}")
    public ResponseEntity<List<BannerResponseDto>> getActiveBanners(@PathVariable BannerSection section) {
        return ResponseEntity.ok(bannerService.getActiveBannersBySection(section));
    }
}
