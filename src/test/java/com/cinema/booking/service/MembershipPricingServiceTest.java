package com.cinema.booking.service;

import com.cinema.booking.entity.MembershipBenefit;
import com.cinema.booking.entity.MembershipPlan;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.MembershipBenefitType;
import com.cinema.booking.enums.MembershipStatus;
import com.cinema.booking.repository.MembershipBenefitRepository;
import com.cinema.booking.repository.MembershipUsageRepository;
import com.cinema.booking.repository.UserMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipPricingServiceTest {

    @Mock private UserMembershipRepository userMembershipRepository;
    @Mock private MembershipBenefitRepository membershipBenefitRepository;
    @Mock private MembershipUsageRepository membershipUsageRepository;

    @Test
    void ticketDiscountIsCalculatedFromActiveMembershipBenefit() {
        MembershipPricingService service = new MembershipPricingService(
                userMembershipRepository,
                membershipBenefitRepository,
                membershipUsageRepository
        );
        User customer = new User();
        customer.setId(7L);

        MembershipPlan plan = new MembershipPlan();
        plan.setId(UUID.randomUUID());

        UserMembership membership = new UserMembership();
        membership.setId(UUID.randomUUID());
        membership.setCustomer(customer);
        membership.setPlan(plan);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setExpiresAt(LocalDateTime.now().plusDays(10));

        MembershipBenefit benefit = new MembershipBenefit();
        benefit.setBenefitType(MembershipBenefitType.TICKET_DISCOUNT);
        benefit.setValue(new BigDecimal("20"));
        benefit.setActive(true);

        when(userMembershipRepository.findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(7L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        when(membershipBenefitRepository.findByPlanIdAndBenefitTypeAndActiveTrue(plan.getId(), MembershipBenefitType.FREE_TICKET))
                .thenReturn(List.of());
        when(membershipBenefitRepository.findByPlanIdAndBenefitTypeAndActiveTrue(plan.getId(), MembershipBenefitType.TICKET_DISCOUNT))
                .thenReturn(List.of(benefit));

        MembershipPricingService.PricingResult result = service.calculateTicketPrice(customer, new BigDecimal("12.50"));

        assertTrue(result.hasDiscount());
        assertEquals(new BigDecimal("12.50"), result.originalAmount());
        assertEquals(new BigDecimal("2.50"), result.discountAmount());
        assertEquals(new BigDecimal("10.00"), result.finalAmount());
        assertEquals(MembershipBenefitType.TICKET_DISCOUNT, result.benefitType());
    }
}
