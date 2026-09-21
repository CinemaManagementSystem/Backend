package com.cinema.booking.mapper;

import com.cinema.booking.dto.membership.MembershipBenefitDto;
import com.cinema.booking.dto.membership.MembershipPlanRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanResponseDto;
import com.cinema.booking.dto.membership.MembershipUsageResponseDto;
import com.cinema.booking.dto.membership.UserMembershipResponseDto;
import com.cinema.booking.entity.MembershipBenefit;
import com.cinema.booking.entity.MembershipPlan;
import com.cinema.booking.entity.MembershipUsage;
import com.cinema.booking.entity.UserMembership;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MembershipMapper {

    public MembershipPlan toPlanEntity(MembershipPlanRequestDto dto) {
        MembershipPlan plan = new MembershipPlan();
        applyPlanRequest(plan, dto);
        return plan;
    }

    public void applyPlanRequest(MembershipPlan plan, MembershipPlanRequestDto dto) {
        plan.setName(dto.name());
        plan.setCode(dto.code().trim().toUpperCase());
        plan.setDescription(dto.description());
        plan.setPrice(dto.price());
        plan.setDurationMonths(dto.durationMonths());
        plan.setActive(dto.active() == null || dto.active());
        plan.setSortOrder(dto.sortOrder());
    }

    public MembershipBenefit toBenefitEntity(MembershipBenefitDto dto, MembershipPlan plan) {
        MembershipBenefit benefit = new MembershipBenefit();
        benefit.setPlan(plan);
        benefit.setBenefitType(dto.benefitType());
        benefit.setValue(dto.value());
        benefit.setMonthlyLimit(dto.monthlyLimit());
        benefit.setActive(dto.active() == null || dto.active());
        return benefit;
    }

    public MembershipPlanResponseDto toPlanResponse(MembershipPlan plan) {
        List<MembershipBenefitDto> benefits = plan.getBenefits() == null ? List.of()
                : plan.getBenefits().stream()
                .sorted(Comparator.comparing(benefit -> benefit.getBenefitType().name()))
                .map(this::toBenefitResponse)
                .toList();
        return new MembershipPlanResponseDto(
                plan.getId(),
                plan.getName(),
                plan.getCode(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getDurationMonths(),
                plan.getActive(),
                plan.getSortOrder(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                benefits
        );
    }

    public MembershipBenefitDto toBenefitResponse(MembershipBenefit benefit) {
        return new MembershipBenefitDto(
                benefit.getId(),
                benefit.getBenefitType(),
                benefit.getValue(),
                benefit.getMonthlyLimit(),
                benefit.getActive()
        );
    }

    public UserMembershipResponseDto toUserMembershipResponse(UserMembership membership, Long paymentId) {
        return new UserMembershipResponseDto(
                membership.getId(),
                membership.getCustomer() != null ? membership.getCustomer().getId() : null,
                membership.getPlan() != null ? membership.getPlan().getId() : null,
                membership.getPlan() != null ? membership.getPlan().getName() : null,
                membership.getPlan() != null ? membership.getPlan().getCode() : null,
                membership.getStatus(),
                membership.getPriceSnapshot(),
                membership.getDurationMonthsSnapshot(),
                membership.getStartedAt(),
                membership.getExpiresAt(),
                membership.getCancelledAt(),
                membership.getCreatedAt(),
                paymentId
        );
    }

    public MembershipUsageResponseDto toUsageResponse(MembershipUsage usage) {
        return new MembershipUsageResponseDto(
                usage.getId(),
                usage.getUserMembership() != null ? usage.getUserMembership().getId() : null,
                usage.getBenefitType(),
                usage.getUsagePeriod(),
                usage.getSourceType(),
                usage.getSourceId(),
                usage.getDiscountAmount(),
                usage.getCreatedAt()
        );
    }
}
