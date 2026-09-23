package com.cinema.booking.service;

import com.cinema.booking.dto.promotions.PageResponseDto;
import com.cinema.booking.dto.promotions.PromotionReportDto;
import com.cinema.booking.dto.promotions.PromotionRequestDto;
import com.cinema.booking.dto.promotions.PromotionResponseDto;
import com.cinema.booking.dto.promotions.PromotionValidationRequestDto;
import com.cinema.booking.dto.promotions.PromotionValidationResponseDto;
import com.cinema.booking.enums.PromotionStatus;

public interface PromotionService {

    PageResponseDto<PromotionResponseDto> list(PromotionStatus status, String type, String search, int page, int size);

    PromotionResponseDto create(PromotionRequestDto dto);

    PromotionResponseDto getById(Long id);

    PromotionResponseDto update(Long id, PromotionRequestDto dto);

    PromotionResponseDto updateStatus(Long id, PromotionStatus status);

    void delete(Long id);

    PromotionReportDto getReport();

    PromotionValidationResponseDto validate(PromotionValidationRequestDto request);
}
