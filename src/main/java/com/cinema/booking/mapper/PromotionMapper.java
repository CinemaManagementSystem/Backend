package com.cinema.booking.mapper;

import com.cinema.booking.dto.promotions.PromotionRequestDto;
import com.cinema.booking.dto.promotions.PromotionResponseDto;
import com.cinema.booking.entity.Promotion;
import com.cinema.booking.enums.PromotionScope;
import com.cinema.booking.enums.PromotionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class PromotionMapper {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    public Promotion toEntity(PromotionRequestDto dto) {
        Promotion entity = new Promotion();
        entity.setName(dto.getName() != null ? dto.getName().trim() : null);
        entity.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        entity.setCode(dto.getCode() != null && !dto.getCode().isBlank() ? dto.getCode().trim().toUpperCase() : null);
        entity.setDiscountType(dto.getDiscountType());
        entity.setDiscountValue(dto.getValue());
        entity.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        entity.setMinOrderAmount(dto.getMinOrderAmount());
        entity.setStartDate(parseDateTime(dto.getStartAt()));
        entity.setEndDate(parseDateTime(dto.getEndAt()));
        entity.setUsageLimitTotal(dto.getUsageLimit());
        entity.setUsageLimitPerUser(dto.getPerUserLimit());
        entity.setScope(dto.getScope() != null ? dto.getScope() : PromotionScope.ALL);

        resolveTargets(entity, dto);
        return entity;
    }

    public void updateEntityFromDto(Promotion entity, PromotionRequestDto dto) {
        entity.setName(dto.getName() != null ? dto.getName().trim() : entity.getName());
        entity.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        entity.setCode(dto.getCode() != null && !dto.getCode().isBlank() ? dto.getCode().trim().toUpperCase() : null);
        entity.setDiscountType(dto.getDiscountType());
        entity.setDiscountValue(dto.getValue());
        entity.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        entity.setMinOrderAmount(dto.getMinOrderAmount());
        if (dto.getStartAt() != null) {
            entity.setStartDate(parseDateTime(dto.getStartAt()));
        }
        if (dto.getEndAt() != null) {
            entity.setEndDate(parseDateTime(dto.getEndAt()));
        }
        entity.setUsageLimitTotal(dto.getUsageLimit());
        entity.setUsageLimitPerUser(dto.getPerUserLimit());
        entity.setScope(dto.getScope() != null ? dto.getScope() : PromotionScope.ALL);

        resolveTargets(entity, dto);
    }

    public PromotionResponseDto toResponseDto(Promotion entity) {
        List<Long> targetIds = new ArrayList<>();
        if (entity.getScope() == PromotionScope.MOVIE && entity.getTargetMovieId() != null) {
            targetIds.add(entity.getTargetMovieId());
        } else if (entity.getScope() == PromotionScope.SHOW && entity.getTargetShowId() != null) {
            targetIds.add(entity.getTargetShowId());
        } else if (entity.getScope() == PromotionScope.PRODUCT && entity.getTargetProductId() != null) {
            targetIds.add(entity.getTargetProductId());
        }

        return PromotionResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .code(entity.getCode())
                .discountType(entity.getDiscountType())
                .value(entity.getDiscountValue())
                .maxDiscountAmount(entity.getMaxDiscountAmount())
                .minOrderAmount(entity.getMinOrderAmount())
                .startAt(entity.getStartDate())
                .endAt(entity.getEndDate())
                .usageLimit(entity.getUsageLimitTotal())
                .perUserLimit(entity.getUsageLimitPerUser())
                .usedCount(entity.getUsedCount() != null ? entity.getUsedCount() : 0)
                .scope(entity.getScope())
                .targetIds(targetIds)
                .targetMovieId(entity.getTargetMovieId())
                .targetShowId(entity.getTargetShowId())
                .targetProductId(entity.getTargetProductId())
                .status(entity.getStatus() != null ? entity.getStatus() : PromotionStatus.DRAFT)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void resolveTargets(Promotion entity, PromotionRequestDto dto) {
        entity.setTargetMovieId(null);
        entity.setTargetShowId(null);
        entity.setTargetProductId(null);

        if (dto.getScope() == PromotionScope.ALL) {
            return;
        }

        Long targetId = null;
        if (dto.getTargetIds() != null && !dto.getTargetIds().isEmpty()) {
            Object first = dto.getTargetIds().get(0);
            if (first != null) {
                try {
                    targetId = Long.valueOf(first.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (dto.getScope() == PromotionScope.MOVIE) {
            entity.setTargetMovieId(targetId != null ? targetId : dto.getTargetMovieId());
        } else if (dto.getScope() == PromotionScope.SHOW) {
            entity.setTargetShowId(targetId != null ? targetId : dto.getTargetShowId());
        } else if (dto.getScope() == PromotionScope.PRODUCT) {
            entity.setTargetProductId(targetId != null ? targetId : dto.getTargetProductId());
        }
    }

    private LocalDateTime parseDateTime(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        try {
            if (trimmed.length() == 16) { // "2026-09-22T13:37"
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            }
            if (trimmed.endsWith("Z")) {
                return Instant.parse(trimmed).atZone(APP_ZONE).toLocalDateTime();
            }
            if (trimmed.contains("+") || trimmed.contains("-") && trimmed.lastIndexOf('-') > 10) {
                return Instant.parse(trimmed).atZone(APP_ZONE).toLocalDateTime();
            }
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(trimmed.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            } catch (Exception ignored) {
                return LocalDateTime.now(APP_ZONE);
            }
        }
    }
}
