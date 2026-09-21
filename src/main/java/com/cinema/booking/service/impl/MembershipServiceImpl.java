package com.cinema.booking.service.impl;

import com.cinema.booking.dto.membership.MembershipExtendRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanRequestDto;
import com.cinema.booking.dto.membership.MembershipPlanResponseDto;
import com.cinema.booking.dto.membership.MembershipSubscribeResponseDto;
import com.cinema.booking.dto.membership.MembershipUsageResponseDto;
import com.cinema.booking.dto.membership.UserMembershipResponseDto;
import com.cinema.booking.dto.payments.PaymentRequestDto;
import com.cinema.booking.dto.payments.PaymentResponseDto;
import com.cinema.booking.entity.MembershipBenefit;
import com.cinema.booking.entity.MembershipPlan;
import com.cinema.booking.entity.Payment;
import com.cinema.booking.entity.User;
import com.cinema.booking.entity.UserMembership;
import com.cinema.booking.enums.MembershipStatus;
import com.cinema.booking.enums.PaymentMethod;
import com.cinema.booking.enums.PaymentStatus;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.mapper.MembershipMapper;
import com.cinema.booking.repository.MembershipPlanRepository;
import com.cinema.booking.repository.MembershipUsageRepository;
import com.cinema.booking.repository.PaymentRepository;
import com.cinema.booking.repository.UserMembershipRepository;
import com.cinema.booking.security.AuthorizationService;
import com.cinema.booking.service.MembershipService;
import com.cinema.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final MembershipUsageRepository membershipUsageRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipMapper membershipMapper;
    private final AuthorizationService authorizationService;
    private final PaymentService paymentService;

    @Override
    @Transactional(readOnly = true)
    public List<MembershipPlanResponseDto> getActivePlans() {
        return membershipPlanRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(membershipMapper::toPlanResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipPlanResponseDto> getAllPlansForAdmin() {
        authorizationService.requireStaffOrAdmin();
        return membershipPlanRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(membershipMapper::toPlanResponse)
                .toList();
    }

    @Override
    @Transactional
    public MembershipPlanResponseDto createPlan(MembershipPlanRequestDto request) {
        authorizationService.requireStaffOrAdmin();
        membershipPlanRepository.findByCodeIgnoreCase(request.code()).ifPresent(existing -> {
            throw new IllegalArgumentException("Membership plan code already exists");
        });
        MembershipPlan plan = membershipMapper.toPlanEntity(request);
        replaceBenefits(plan, request);
        return membershipMapper.toPlanResponse(membershipPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public MembershipPlanResponseDto updatePlan(UUID id, MembershipPlanRequestDto request) {
        authorizationService.requireStaffOrAdmin();
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipPlan", id));
        membershipPlanRepository.findByCodeIgnoreCase(request.code()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException("Membership plan code already exists");
            }
        });
        membershipMapper.applyPlanRequest(plan, request);
        replaceBenefits(plan, request);
        return membershipMapper.toPlanResponse(membershipPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public void deletePlan(UUID id) {
        authorizationService.requireStaffOrAdmin();
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipPlan", id));
        plan.setActive(false);
        membershipPlanRepository.save(plan);
    }

    @Override
    @Transactional
    public MembershipSubscribeResponseDto subscribe(UUID planId) {
        User customer = authorizationService.getCurrentUser();
        List<UserMembership> activeMemberships = userMembershipRepository
                .findByCustomerIdAndStatusForUpdate(customer.getId(), MembershipStatus.ACTIVE);
        if (!activeMemberships.isEmpty()) {
            throw new IllegalStateException("You already have an active membership");
        }

        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipPlan", planId));
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new IllegalStateException("This membership plan is not available");
        }

        UserMembership membership = new UserMembership();
        membership.setCustomer(customer);
        membership.setPlan(plan);
        membership.setStatus(MembershipStatus.PENDING_PAYMENT);
        membership.setPriceSnapshot(plan.getPrice());
        membership.setDurationMonthsSnapshot(plan.getDurationMonths());
        membership = userMembershipRepository.save(membership);

        PaymentResponseDto payment = paymentService.create(new PaymentRequestDto(
                plan.getPrice(),
                PaymentMethod.KHQR,
                customer.getId(),
                null,
                null,
                membership.getId(),
                null,
                null
        ));
        return new MembershipSubscribeResponseDto(
                membershipMapper.toUserMembershipResponse(membership, payment.id()),
                payment
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserMembershipResponseDto getMyMembership() {
        User customer = authorizationService.getCurrentUser();
        return userMembershipRepository
                .findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(customer.getId(), MembershipStatus.ACTIVE)
                .map(membership -> membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership)))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserMembershipResponseDto> getMyMembershipHistory() {
        User customer = authorizationService.getCurrentUser();
        return userMembershipRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(membership -> membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipUsageResponseDto> getMyUsage() {
        User customer = authorizationService.getCurrentUser();
        return userMembershipRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .flatMap(membership -> membershipUsageRepository.findByUserMembershipIdOrderByCreatedAtDesc(membership.getId()).stream())
                .map(membershipMapper::toUsageResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserMembershipResponseDto cancelMyMembership(UUID membershipId) {
        UserMembership membership = userMembershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("UserMembership", membershipId));
        authorizationService.requireOwnerOrStaff(membership.getCustomer());
        return cancelMembership(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserMembershipResponseDto> getAllMembersForAdmin() {
        authorizationService.requireStaffOrAdmin();
        return userMembershipRepository.findAll().stream()
                .sorted(Comparator.comparing(UserMembership::getCreatedAt).reversed())
                .map(membership -> membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership)))
                .toList();
    }

    @Override
    @Transactional
    public UserMembershipResponseDto cancelMembershipForAdmin(UUID membershipId) {
        authorizationService.requireStaffOrAdmin();
        UserMembership membership = userMembershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("UserMembership", membershipId));
        return cancelMembership(membership);
    }

    @Override
    @Transactional
    public UserMembershipResponseDto extendMembershipForAdmin(UUID membershipId, MembershipExtendRequestDto request) {
        authorizationService.requireStaffOrAdmin();
        UserMembership membership = userMembershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("UserMembership", membershipId));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalStateException("Only active memberships can be extended");
        }
        LocalDateTime base = membership.getExpiresAt() != null && membership.getExpiresAt().isAfter(now())
                ? membership.getExpiresAt()
                : now();
        membership.setExpiresAt(base.plusMonths(request.months()));
        membership = userMembershipRepository.save(membership);
        return membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership));
    }

    @Override
    @Transactional
    public void expireMemberships() {
        LocalDateTime now = now();
        List<UserMembership> expired = userMembershipRepository.findByStatusAndExpiresAtBefore(MembershipStatus.ACTIVE, now);
        expired.forEach(membership -> membership.setStatus(MembershipStatus.EXPIRED));
        userMembershipRepository.saveAll(expired);
    }

    private UserMembershipResponseDto cancelMembership(UserMembership membership) {
        if (membership.getStatus() == MembershipStatus.CANCELLED || membership.getStatus() == MembershipStatus.EXPIRED) {
            return membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership));
        }
        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setCancelledAt(now());
        membership = userMembershipRepository.save(membership);
        paymentRepository.findByUserMembershipIdAndStatusOrderByIdAsc(membership.getId(), PaymentStatus.PENDING)
                .forEach(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
        return membershipMapper.toUserMembershipResponse(membership, paymentIdFor(membership));
    }

    private void replaceBenefits(MembershipPlan plan, MembershipPlanRequestDto request) {
        plan.getBenefits().clear();
        if (request.benefits() == null) {
            return;
        }
        request.benefits().forEach(dto -> {
            MembershipBenefit benefit = membershipMapper.toBenefitEntity(dto, plan);
            plan.getBenefits().add(benefit);
        });
    }

    private Long paymentIdFor(UserMembership membership) {
        return paymentRepository.findByUserMembershipId(membership.getId()).stream()
                .max(Comparator.comparing(Payment::getId))
                .map(Payment::getId)
                .orElse(null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
    }
}
