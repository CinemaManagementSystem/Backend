package com.cinema.booking.service;

import com.cinema.booking.dto.membership.MembershipExtendRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanResponseDto;
import com.cinema.booking.dto.membership.MembershipSubscribeResponseDto;
import com.cinema.booking.dto.membership.MembershipUsageResponseDto;
import com.cinema.booking.dto.membership.UserMembershipResponseDto;

import java.util.List;
import java.util.UUID;

public interface MembershipService {
    List<MembershipPlanResponseDto> getActivePlans();
    List<MembershipPlanResponseDto> getAllPlansForAdmin();
    MembershipPlanResponseDto createPlan(MembershipPlanRequestDto request);
    MembershipPlanResponseDto updatePlan(UUID id, MembershipPlanRequestDto request);
    void deletePlan(UUID id);
    MembershipSubscribeResponseDto subscribe(UUID planId);
    UserMembershipResponseDto getMyMembership();
    List<UserMembershipResponseDto> getMyMembershipHistory();
    List<MembershipUsageResponseDto> getMyUsage();
    UserMembershipResponseDto cancelMyMembership(UUID membershipId);
    List<UserMembershipResponseDto> getAllMembersForAdmin();
    UserMembershipResponseDto cancelMembershipForAdmin(UUID membershipId);
    UserMembershipResponseDto extendMembershipForAdmin(UUID membershipId, MembershipExtendRequestDto request);
    void expireMemberships();
}
