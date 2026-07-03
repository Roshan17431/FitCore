package com.fitcore.gym.service;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.dto.membership.ActiveMembershipDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.ActiveMembershipRepository;
import com.fitcore.gym.repository.MembershipPlanRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipPlanRepository planRepository;
    private final ActiveMembershipRepository activeMembershipRepository;
    private final UserRepository userRepository;

    public List<MembershipPlanDto> getAllPlans() {
        return planRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ActiveMembershipDto subscribeUserToPlan(UUID userId, Long planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        MembershipPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        ActiveMembership membership = ActiveMembership.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(plan.getDurationMonths()))
                .status(MembershipStatus.ACTIVE)
                .build();

        return mapToDto(activeMembershipRepository.save(membership));
    }

    public List<ActiveMembershipDto> getUserMemberships(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return activeMembershipRepository.findByUserOrderByStartDateDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ActiveMembershipDto getCurrentMembership(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return activeMembershipRepository.findFirstByUserOrderByStartDateDesc(user)
                .map(this::mapToDto)
                .orElse(null);
    }

    public MembershipPlanDto mapToDto(MembershipPlan plan) {
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
        return ActiveMembershipDto.builder()
                .id(membership.getId())
                .userId(membership.getUser().getId())
                .plan(mapToDto(membership.getPlan()))
                .startDate(membership.getStartDate())
                .endDate(membership.getEndDate())
                .status(membership.getStatus())
                .build();
    }
}
