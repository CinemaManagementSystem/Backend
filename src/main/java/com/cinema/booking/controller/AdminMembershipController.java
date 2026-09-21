package com.cinema.booking.controller;

import com.cinema.booking.dto.membership.MembershipExtendRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanResponseDto;
import com.cinema.booking.dto.membership.UserMembershipResponseDto;
import com.cinema.booking.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/memberships")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class AdminMembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    public ResponseEntity<List<MembershipPlanResponseDto>> getPlans() {
        return ResponseEntity.ok(membershipService.getAllPlansForAdmin());
    }

    @PostMapping("/plans")
    public ResponseEntity<MembershipPlanResponseDto> createPlan(@Valid @RequestBody MembershipPlanRequestDto request) {
        return new ResponseEntity<>(membershipService.createPlan(request), HttpStatus.CREATED);
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<MembershipPlanResponseDto> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody MembershipPlanRequestDto request
    ) {
        return ResponseEntity.ok(membershipService.updatePlan(planId, request));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID planId) {
        membershipService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members")
    public ResponseEntity<List<UserMembershipResponseDto>> getMembers() {
        return ResponseEntity.ok(membershipService.getAllMembersForAdmin());
    }

    @PostMapping("/members/{membershipId}/cancel")
    public ResponseEntity<UserMembershipResponseDto> cancelMembership(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(membershipService.cancelMembershipForAdmin(membershipId));
    }

    @PostMapping("/members/{membershipId}/extend")
    public ResponseEntity<UserMembershipResponseDto> extendMembership(
            @PathVariable UUID membershipId,
            @Valid @RequestBody MembershipExtendRequestDto request
    ) {
        return ResponseEntity.ok(membershipService.extendMembershipForAdmin(membershipId, request));
    }
}
