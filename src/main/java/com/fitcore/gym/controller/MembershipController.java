package com.fitcore.gym.controller;

import com.fitcore.gym.dto.membership.ActiveMembershipDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    public ResponseEntity<List<MembershipPlanDto>> getAllPlans() {
        return ResponseEntity.ok(membershipService.getAllPlans());
    }

    @PostMapping("/subscribe/{userId}/{planId}")
    public ResponseEntity<ActiveMembershipDto> subscribeToPlan(
            @PathVariable UUID userId,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok(membershipService.subscribeUserToPlan(userId, planId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ActiveMembershipDto>> getUserMemberships(@PathVariable UUID userId) {
        return ResponseEntity.ok(membershipService.getUserMemberships(userId));
    }
}
