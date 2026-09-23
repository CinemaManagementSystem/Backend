package com.cinema.booking.service.impl;

import com.cinema.booking.dto.promotions.PageResponseDto;
import com.cinema.booking.dto.promotions.PromotionReportDto;
import com.cinema.booking.dto.promotions.PromotionRequestDto;
import com.cinema.booking.dto.promotions.PromotionResponseDto;
import com.cinema.booking.dto.promotions.PromotionValidationRequestDto;
import com.cinema.booking.dto.promotions.PromotionValidationResponseDto;
import com.cinema.booking.entity.Promotion;
import com.cinema.booking.entity.PromotionUsage;
import com.cinema.booking.enums.PromotionDiscountType;
import com.cinema.booking.enums.PromotionStatus;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.PromotionMapper;
import com.cinema.booking.repository.PromotionRepository;
import com.cinema.booking.repository.PromotionUsageRepository;
import com.cinema.booking.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PromotionMapper promotionMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<PromotionResponseDto> list(PromotionStatus status, String type, String search, int page, int size) {
        List<Promotion> all = promotionRepository.findAll();

        String normalizedType = type != null ? type.trim().toUpperCase() : null;
        if ("FIXED".equals(normalizedType)) {
            normalizedType = "FIXED_AMOUNT";
        }

        final String finalType = normalizedType;
        final String finalSearch = search != null ? search.trim().toLowerCase() : null;

        List<PromotionResponseDto> filtered = all.stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> finalType == null || p.getDiscountType().name().equalsIgnoreCase(finalType))
                .filter(p -> {
                    if (finalSearch == null || finalSearch.isBlank()) return true;
                    boolean matchesName = p.getName() != null && p.getName().toLowerCase().contains(finalSearch);
                    boolean matchesCode = p.getCode() != null && p.getCode().toLowerCase().contains(finalSearch);
                    return matchesName || matchesCode;
                })
                .sorted(Comparator.comparing(Promotion::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(promotionMapper::toResponseDto)
                .collect(Collectors.toList());

        int pageSize = Math.max(1, size);
        int pageNumber = Math.max(0, page);
        int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<PromotionResponseDto> pageContent = filtered.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) filtered.size() / pageSize);

        return new PageResponseDto<>(
                pageContent,
                filtered.size(),
                Math.max(1, totalPages),
                pageNumber,
                pageSize
        );
    }

    @Override
    public PromotionResponseDto create(PromotionRequestDto dto) {
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            String code = dto.getCode().trim().toUpperCase();
            if (promotionRepository.findByCodeIgnoreCase(code).isPresent()) {
                throw new IllegalArgumentException("Promotion code already exists: " + code);
            }
        }

        Promotion entity = promotionMapper.toEntity(dto);
        if (entity.getStatus() == null || entity.getStatus() == PromotionStatus.DRAFT) {
            entity.setStatus(PromotionStatus.ACTIVE);
        }
        if (entity.getUsedCount() == null) {
            entity.setUsedCount(0);
        }

        Promotion saved = promotionRepository.save(entity);
        return promotionMapper.toResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponseDto getById(Long id) {
        Promotion entity = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));
        return promotionMapper.toResponseDto(entity);
    }

    @Override
    public PromotionResponseDto update(Long id, PromotionRequestDto dto) {
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));

        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            String code = dto.getCode().trim().toUpperCase();
            promotionRepository.findByCodeIgnoreCase(code).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new IllegalArgumentException("Promotion code already exists: " + code);
                }
            });
        }

        promotionMapper.updateEntityFromDto(existing, dto);
        Promotion saved = promotionRepository.save(existing);
        return promotionMapper.toResponseDto(saved);
    }

    @Override
    public PromotionResponseDto updateStatus(Long id, PromotionStatus status) {
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));
        existing.setStatus(status);
        Promotion saved = promotionRepository.save(existing);
        return promotionMapper.toResponseDto(saved);
    }

    @Override
    public void delete(Long id) {
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));
        promotionRepository.delete(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionReportDto getReport() {
        List<PromotionUsage> usages = promotionUsageRepository.findAll();

        long totalUses = usages.size();
        BigDecimal totalDiscountGiven = usages.stream()
                .map(PromotionUsage::getDiscountApplied)
                .filter(val -> val != null && val.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenueFromPromoOrders = usages.stream()
                .map(PromotionUsage::getOrder)
                .filter(order -> order != null && order.getTotalAmount() != null)
                .map(order -> order.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PromotionReportDto.PromotionUsageDto> usageDtos = usages.stream()
                .map(u -> PromotionReportDto.PromotionUsageDto.builder()
                        .id(String.valueOf(u.getId()))
                        .promotionId(u.getPromotion() != null ? String.valueOf(u.getPromotion().getId()) : null)
                        .userId(u.getUser() != null ? String.valueOf(u.getUser().getId()) : null)
                        .userName(u.getUser() != null ? u.getUser().getName() : null)
                        .orderId(u.getOrder() != null ? String.valueOf(u.getOrder().getId()) : null)
                        .discountApplied(u.getDiscountApplied() != null ? u.getDiscountApplied() : BigDecimal.ZERO)
                        .usedAt(u.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PromotionReportDto.builder()
                .summary(PromotionReportDto.PromotionReportSummaryDto.builder()
                        .totalUses(totalUses)
                        .totalDiscountGiven(totalDiscountGiven)
                        .revenueFromPromoOrders(revenueFromPromoOrders)
                        .build())
                .usages(usageDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionValidationResponseDto validate(PromotionValidationRequestDto request) {
        if (request.getCode() == null || request.getCode().trim().isBlank()) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion code is required")
                    .build();
        }

        String code = request.getCode().trim().toUpperCase();
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code).orElse(null);
        if (promotion == null) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Invalid promotion code")
                    .code(code)
                    .build();
        }

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion is currently " + promotion.getStatus().name().toLowerCase())
                    .code(code)
                    .promotionId(String.valueOf(promotion.getId()))
                    .build();
        }

        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        if (promotion.getStartDate() != null && now.isBefore(promotion.getStartDate())) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion has not started yet")
                    .code(code)
                    .promotionId(String.valueOf(promotion.getId()))
                    .build();
        }

        if (promotion.getEndDate() != null && now.isAfter(promotion.getEndDate())) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion has expired")
                    .code(code)
                    .promotionId(String.valueOf(promotion.getId()))
                    .build();
        }

        BigDecimal subtotal = request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO;
        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Minimum order of $" + promotion.getMinOrderAmount() + " required to use this code")
                    .code(code)
                    .promotionId(String.valueOf(promotion.getId()))
                    .build();
        }

        if (promotion.getUsageLimitTotal() != null && promotion.getUsedCount() != null && promotion.getUsedCount() >= promotion.getUsageLimitTotal()) {
            return PromotionValidationResponseDto.builder()
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion usage limit has been reached")
                    .code(code)
                    .promotionId(String.valueOf(promotion.getId()))
                    .build();
        }

        BigDecimal discount;
        if (promotion.getDiscountType() == PromotionDiscountType.PERCENT) {
            BigDecimal rate = promotion.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discount = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                discount = promotion.getMaxDiscountAmount();
            }
        } else {
            discount = promotion.getDiscountValue();
        }

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        BigDecimal total = subtotal.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        return PromotionValidationResponseDto.builder()
                .valid(true)
                .discountAmount(discount)
                .message("Promotion applied successfully")
                .code(code)
                .promotionId(String.valueOf(promotion.getId()))
                .total(total)
                .build();
    }
}
