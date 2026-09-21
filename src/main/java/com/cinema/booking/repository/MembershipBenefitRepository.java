package com.cinema.booking.repository;

import com.cinema.booking.entity.MembershipBenefit;
import com.cinema.booking.enums.MembershipBenefitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MembershipBenefitRepository extends JpaRepository<MembershipBenefit, UUID> {
    List<MembershipBenefit> findByPlanIdOrderByBenefitTypeAsc(UUID planId);
    List<MembershipBenefit> findByPlanIdAndActiveTrue(UUID planId);
    List<MembershipBenefit> findByPlanIdAndBenefitTypeAndActiveTrue(UUID planId, MembershipBenefitType benefitType);
}
