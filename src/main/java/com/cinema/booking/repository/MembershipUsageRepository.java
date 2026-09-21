package com.cinema.booking.repository;

import com.cinema.booking.entity.MembershipUsage;
import com.cinema.booking.enums.MembershipBenefitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MembershipUsageRepository extends JpaRepository<MembershipUsage, UUID> {
    List<MembershipUsage> findByUserMembershipIdOrderByCreatedAtDesc(UUID userMembershipId);
    long countByUserMembershipIdAndBenefitTypeAndUsagePeriod(UUID userMembershipId, MembershipBenefitType benefitType, String usagePeriod);
    boolean existsByUserMembershipIdAndBenefitTypeAndUsagePeriodAndSourceTypeAndSourceId(
            UUID userMembershipId,
            MembershipBenefitType benefitType,
            String usagePeriod,
            String sourceType,
            Long sourceId
    );
}
