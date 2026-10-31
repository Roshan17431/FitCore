package com.fitcore.gym.service;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.dto.membership.ActiveMembershipDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.BadRequestException;
import com.fitcore.gym.exception.DuplicateResourceException;
import com.fitcore.gym.exception.ResourceNotFoundException;
import com.fitcore.gym.repository.ActiveMembershipRepository;
import com.fitcore.gym.repository.MembershipPlanRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class MembershipService {
    private final TenantAccess tenantAccess;
    private final MembershipPlanRepository planRepository;
    private final ActiveMembershipRepository activeMembershipRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public List<MembershipPlanDto> getAllPlans() {
        return planRepository.findAllByGymId(tenantAccess.gym()).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public ActiveMembershipDto subscribeUserToPlan(UUID userId, Long planId) {
        User user = lockUser(userId);
        LocalDate date = today();
        MembershipPlan plan = validPlan(planId);
        List<ActiveMembership> history =
                activeMembershipRepository.findByGymIdAndUserIdOrderByStartDateDesc(
                        tenantAccess.gym(), user.getId());
        requireNoUnfinishedMembership(history, date);
        return mapToDto(createPeriod(user, plan, date, date.plusMonths(plan.getDurationMonths())));
    }

    @Transactional
    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public ActiveMembershipDto renewMembership(
            UUID userId, Long membershipId, LocalDate expectedEndDate) {
        User user = lockUser(userId);
        LocalDate date = today();
        List<ActiveMembership> history =
                activeMembershipRepository.findByGymIdAndUserIdOrderByStartDateDesc(
                        tenantAccess.gym(), user.getId());
        ActiveMembership target = ownedPeriod(history, membershipId);
        requireExpectedState(target, expectedEndDate);
        if (target.getStatus() == MembershipStatus.CANCELLED) {
            throw conflict("Cancelled memberships cannot be renewed. Refresh membership history.");
        }
        MembershipPlan plan = validPlan(target.getPlan().getId());
        ActiveMembership current = currentPeriod(history, date);
        if (current != null && current.getId().equals(target.getId())) {
            LocalDate extendedEnd = target.getEndDate().plusMonths(plan.getDurationMonths());
            boolean overlapsAnother =
                    history.stream()
                            .anyMatch(
                                    item ->
                                            !item.getId().equals(target.getId())
                                                    && item.getStatus() == MembershipStatus.ACTIVE
                                                    && item.getStartDate().isBefore(extendedEnd)
                                                    && item.getEndDate()
                                                            .isAfter(target.getEndDate()));
            if (overlapsAnother)
                throw conflict(
                        "Renewal would overlap another membership. Ask an administrator to review"
                            + " the records.");
            target.setEndDate(extendedEnd);
            return mapToDto(activeMembershipRepository.save(target));
        }
        if (target.getEndDate().isAfter(date)) {
            throw conflict("Only a current or expired membership can be renewed.");
        }
        requireNoUnfinishedMembership(history, date);
        ActiveMembership latest =
                history.stream()
                        .filter(item -> item.getStatus() != MembershipStatus.CANCELLED)
                        .max(
                                Comparator.comparing(ActiveMembership::getStartDate)
                                        .thenComparing(ActiveMembership::getId))
                        .orElseThrow();
        if (!latest.getId().equals(target.getId())) {
            throw conflict(
                    "Membership history has changed. Refresh and renew the latest membership.");
        }
        return mapToDto(createPeriod(user, plan, date, date.plusMonths(plan.getDurationMonths())));
    }

    @Transactional
    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public ActiveMembershipDto upgradeMembership(
            UUID userId, Long planId, Long membershipId, LocalDate expectedEndDate) {
        User user = lockUser(userId);
        LocalDate date = today();
        List<ActiveMembership> history =
                activeMembershipRepository.findByGymIdAndUserIdOrderByStartDateDesc(
                        tenantAccess.gym(), user.getId());
        ActiveMembership target = ownedPeriod(history, membershipId);
        requireExpectedState(target, expectedEndDate);
        ActiveMembership current = currentPeriod(history, date);
        if (current == null || !current.getId().equals(target.getId())) {
            throw conflict(
                    "Only the current membership can be upgraded. Refresh membership details.");
        }
        MembershipPlan next = validPlan(planId);
        // Prices describe complete packages; no payment or proration is performed here.
        if (next.getPrice().compareTo(current.getPlan().getPrice()) <= 0) {
            throw conflict("An upgrade must select a higher-priced package.");
        }
        LocalDate retainedEnd = current.getEndDate();
        current.setStatus(MembershipStatus.CANCELLED);
        activeMembershipRepository.saveAndFlush(current);
        return mapToDto(createPeriod(user, next, date, retainedEnd));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<ActiveMembershipDto> getUserMemberships(UUID userId) {
        User user = tenantAccess.requireUser(userId);
        return activeMembershipRepository
                .findByGymIdAndUserIdOrderByStartDateDesc(tenantAccess.gym(), user.getId())
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public ActiveMembershipDto getCurrentMembership(UUID userId) {
        User user = tenantAccess.requireUser(userId);
        ActiveMembership current =
                currentPeriod(
                        activeMembershipRepository.findByGymIdAndUserIdOrderByStartDateDesc(
                                tenantAccess.gym(), user.getId()),
                        today());
        return current == null ? null : mapToDto(current);
    }

    private User lockUser(UUID id) {
        User user =
                userRepository
                        .findByIdAndGymIdForUpdate(id, tenantAccess.gym())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.isEnabled())
            throw new BadRequestException("Membership changes require an active account");
        return user;
    }

    private MembershipPlan validPlan(Long id) {
        MembershipPlan plan =
                planRepository
                        .findByIdAndGymId(id, tenantAccess.gym())
                        .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        if (plan.getDurationMonths() == null
                || plan.getDurationMonths() <= 0
                || plan.getPrice() == null
                || plan.getPrice().signum() <= 0) {
            throw new BadRequestException(
                    "Membership plan must have a positive duration and package price");
        }
        return plan;
    }

    private ActiveMembership ownedPeriod(List<ActiveMembership> history, Long id) {
        return history.stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
    }

    private void requireExpectedState(ActiveMembership target, LocalDate expectedEndDate) {
        if (expectedEndDate == null)
            throw new BadRequestException("Expected membership end date is required");
        if (!expectedEndDate.equals(target.getEndDate())) {
            throw conflict("Membership has changed. Refresh details before trying again.");
        }
    }

    private void requireNoUnfinishedMembership(List<ActiveMembership> history, LocalDate date) {
        if (history.stream()
                .anyMatch(
                        item ->
                                item.getStatus() == MembershipStatus.ACTIVE
                                        && item.getEndDate().isAfter(date))) {
            throw conflict(
                    "A current or upcoming membership already exists. Use renewal or upgrade"
                        + " instead.");
        }
    }

    private ActiveMembership currentPeriod(List<ActiveMembership> history, LocalDate date) {
        List<ActiveMembership> current =
                history.stream().filter(item -> item.isCurrentOn(date)).toList();
        if (current.size() > 1) {
            throw conflict(
                    "Multiple current memberships require administrator review. Existing records"
                        + " were not changed.");
        }
        return current.isEmpty() ? null : current.get(0);
    }

    private ActiveMembership createPeriod(
            User user, MembershipPlan plan, LocalDate start, LocalDate end) {
        return activeMembershipRepository.save(
                ActiveMembership.builder()
                        .gymId(user.getGymId())
                        .user(user)
                        .plan(plan)
                        .startDate(start)
                        .endDate(end)
                        .status(MembershipStatus.ACTIVE)
                        .build());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private DuplicateResourceException conflict(String message) {
        return new DuplicateResourceException(message);
    }

    public MembershipPlanDto mapToDto(MembershipPlan plan) {
        tenantAccess.requireOwned(plan);
        return MembershipPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .price(plan.getPrice())
                .durationMonths(plan.getDurationMonths())
                .features(plan.getFeatures())
                .recommended(plan.getRecommended())
                .build();
    }

    public ActiveMembershipDto mapToDto(ActiveMembership membership) {
        tenantAccess.requireOwned(membership);
        LocalDate date = today();
        MembershipStatus status = membership.getStatus();
        if (status == MembershipStatus.ACTIVE && !membership.getEndDate().isAfter(date))
            status = MembershipStatus.EXPIRED;
        String effective =
                status == MembershipStatus.ACTIVE && membership.getStartDate().isAfter(date)
                        ? "UPCOMING"
                        : status.name();
        return ActiveMembershipDto.builder()
                .id(membership.getId())
                .userId(membership.getUser().getId())
                .plan(mapToDto(membership.getPlan()))
                .startDate(membership.getStartDate())
                .endDate(membership.getEndDate())
                .status(status)
                .effectiveStatus(effective)
                .serverDate(date)
                .timeZone("UTC")
                .remainingDays(
                        membership.isCurrentOn(date)
                                ? ChronoUnit.DAYS.between(date, membership.getEndDate())
                                : 0)
                .build();
    }
}
