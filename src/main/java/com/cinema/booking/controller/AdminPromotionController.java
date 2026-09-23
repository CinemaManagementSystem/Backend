package com.cinema.booking.controller;

import com.cinema.booking.dto.promotions.PageResponseDto;
import com.cinema.booking.dto.promotions.PromotionReportDto;
import com.cinema.booking.dto.promotions.PromotionRequestDto;
import com.cinema.booking.dto.promotions.PromotionResponseDto;
import com.cinema.booking.enums.PromotionStatus;
import com.cinema.booking.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public ResponseEntity<PageResponseDto<PromotionResponseDto>> list(
            @RequestParam(required = false) PromotionStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(promotionService.list(status, type, search, page, size));
    }

    @PostMapping
    public ResponseEntity<PromotionResponseDto> create(@Valid @RequestBody PromotionRequestDto dto) {
        return new ResponseEntity<>(promotionService.create(dto), HttpStatus.CREATED);
    }

    @GetMapping("/report")
    public ResponseEntity<PromotionReportDto> getReport() {
        return ResponseEntity.ok(promotionService.getReport());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PromotionRequestDto dto
    ) {
        return ResponseEntity.ok(promotionService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PromotionResponseDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        PromotionStatus status = PromotionStatus.valueOf(statusStr.trim().toUpperCase());
        return ResponseEntity.ok(promotionService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
