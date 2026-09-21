package com.cinema.booking.service;

import com.cinema.booking.entity.MembershipBenefit;
import com.cinema.booking.entity.MembershipUsage;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.MembershipBenefitType;
import com.cinema.booking.enums.MembershipStatus;
import com.cinema.booking.repository.MembershipBenefitRepository;
import com.cinema.booking.repository.MembershipUsageRepository;
import com.cinema.booking.repository.UserMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipPricingService {

    private final UserMembershipRepository userMembershipRepository;
    private final MembershipBenefitRepository membershipBenefitRepository;
    private final MembershipUsageRepository membershipUsageRepository;

    public record PricingResult(
            UserMembership membership,
            MembershipBenefitType benefitType,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            boolean shouldRecordUsage
    ) {
        public boolean hasDiscount() {
            return discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    @Transactional(readOnly = true)
    public PricingResult calculateTicketPrice(User customer, BigDecimal seatPrice) {
        return calculate(customer, seatPrice, MembershipBenefitType.FREE_TICKET, MembershipBenefitType.TICKET_DISCOUNT);
    }

    @Transactional(readOnly = true)
    public PricingResult calculateFoodUnitPrice(User customer, BigDecimal productPrice) {
        return calculate(customer, productPrice, null, MembershipBenefitType.FOOD_DISCOUNT);
    }

    @Transactional
    public void recordUsage(PricingResult result, String sourceType, Long sourceId) {
        if (result == null || !result.hasDiscount() || !result.shouldRecordUsage()
                || result.membership() == null || result.benefitType() == null || sourceId == null) {
            return;
        }
        String period = currentPeriod();
        boolean exists = membershipUsageRepository.existsByUserMembershipIdAndBenefitTypeAndUsagePeriodAndSourceTypeAndSourceId(
                result.membership().getId(),
                result.benefitType(),
                period,
                sourceType,
                sourceId
        );
        if (exists) {
            return;
        }
        MembershipUsage usage = new MembershipUsage();
        usage.setUserMembership(result.membership());
        usage.setBenefitType(result.benefitType());
        usage.setUsagePeriod(period);
        usage.setSourceType(sourceType);
        usage.setSourceId(sourceId);
        usage.setDiscountAmount(result.discountAmount());
        membershipUsageRepository.save(usage);
    }

    private PricingResult calculate(User customer, BigDecimal amount, MembershipBenefitType fixedBenefit, MembershipBenefitType percentageBenefit) {
        BigDecimal original = normalize(amount);
        UserMembership active = findActiveMembership(customer);
        if (active == null || original.compareTo(BigDecimal.ZERO) <= 0) {
            return noDiscount(original);
        }

        if (fixedBenefit != null) {
            MembershipBenefit freeBenefit = bestBenefit(active, fixedBenefit);
            if (freeBenefit != null && hasMonthlyCapacity(active, fixedBenefit, freeBenefit.getMonthlyLimit())) {
                return new PricingResult(active, fixedBenefit, original, original, BigDecimal.ZERO, true);
            }
        }

        MembershipBenefit discountBenefit = bestBenefit(active, percentageBenefit);
        if (discountBenefit == null || discountBenefit.getValue() == null
                || discountBenefit.getValue().compareTo(BigDecimal.ZERO) <= 0
                || !hasMonthlyCapacity(active, percentageBenefit, discountBenefit.getMonthlyLimit())) {
            return noDiscount(original);
        }

        BigDecimal percent = discountBenefit.getValue().min(BigDecimal.valueOf(100));
        BigDecimal discount = original.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .min(original);
        return new PricingResult(active, percentageBenefit, original, discount, original.subtract(discount), true);
    }

    private UserMembership findActiveMembership(User customer) {
        if (customer == null || customer.getId() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
        return userMembershipRepository
                .findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(customer.getId(), MembershipStatus.ACTIVE)
                .filter(membership -> membership.getExpiresAt() == null || membership.getExpiresAt().isAfter(now))
                .orElse(null);
    }

    private MembershipBenefit bestBenefit(UserMembership membership, MembershipBenefitType type) {
        if (type == null || membership.getPlan() == null) {
            return null;
        }
        List<MembershipBenefit> benefits = membershipBenefitRepository
                .findByPlanIdAndBenefitTypeAndActiveTrue(membership.getPlan().getId(), type);
        return benefits.stream()
                .max(Comparator.comparing(MembershipBenefit::getValue))
                .orElse(null);
    }

    private boolean hasMonthlyCapacity(UserMembership membership, MembershipBenefitType type, Integer limit) {
        if (limit == null || limit <= 0) {
            return true;
        }
        long used = membershipUsageRepository.countByUserMembershipIdAndBenefitTypeAndUsagePeriod(
                membership.getId(),
                type,
                currentPeriod()
        );
        return used < limit;
    }

    private PricingResult noDiscount(BigDecimal amount) {
        return new PricingResult(null, null, amount, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), amount, false);
    }

    private BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String currentPeriod() {
        return YearMonth.now(ZoneId.of("Asia/Phnom_Penh")).toString();
    }
}
