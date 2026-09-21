package com.cinema.booking.controller;

import com.cinema.booking.dto.membership.MembershipPlanResponseDto;
import com.cinema.booking.dto.membership.MembershipSubscribeResponseDto;
import com.cinema.booking.dto.membership.MembershipUsageResponseDto;
import com.cinema.booking.dto.membership.UserMembershipResponseDto;
import com.cinema.booking.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    public ResponseEntity<List<MembershipPlanResponseDto>> getActivePlans() {
        return ResponseEntity.ok(membershipService.getActivePlans());
    }

    @PostMapping("/plans/{planId}/subscribe")
    public ResponseEntity<MembershipSubscribeResponseDto> subscribe(@PathVariable UUID planId) {
        return new ResponseEntity<>(membershipService.subscribe(planId), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<UserMembershipResponseDto> getMyMembership() {
        return ResponseEntity.ok(membershipService.getMyMembership());
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<UserMembershipResponseDto>> getMyMembershipHistory() {
        return ResponseEntity.ok(membershipService.getMyMembershipHistory());
    }

    @GetMapping("/me/usage")
    public ResponseEntity<List<MembershipUsageResponseDto>> getMyUsage() {
        return ResponseEntity.ok(membershipService.getMyUsage());
    }

    @DeleteMapping("/me/{membershipId}")
    public ResponseEntity<UserMembershipResponseDto> cancelMyMembership(@PathVariable UUID membershipId) {
        return ResponseEntity.ok(membershipService.cancelMyMembership(membershipId));
    }
}
