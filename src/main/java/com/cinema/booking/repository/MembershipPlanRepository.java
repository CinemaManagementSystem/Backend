package com.cinema.booking.repository;

import com.cinema.booking.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {
    List<MembershipPlan> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<MembershipPlan> findAllByOrderBySortOrderAscNameAsc();
    Optional<MembershipPlan> findByCodeIgnoreCase(String code);
}
