package com.cinema.booking.controller;

import com.cinema.booking.dto.promotions.PromotionValidationRequestDto;
import com.cinema.booking.dto.promotions.PromotionValidationResponseDto;
import com.cinema.booking.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("/validate")
    public ResponseEntity<PromotionValidationResponseDto> validate(
            @RequestBody PromotionValidationRequestDto request
    ) {
        return ResponseEntity.ok(promotionService.validate(request));
    }
}
