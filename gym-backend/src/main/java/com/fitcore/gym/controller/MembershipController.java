package com.fitcore.gym.controller;

import com.fitcore.gym.dto.membership.ActiveMembershipDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
@Tag(name = "Membership", description = "Endpoints for managing gym memberships")
@SecurityRequirement(name = "Bearer Authentication")
public class MembershipController {

    private final MembershipService membershipService;

    @Operation(
            summary = "Get all membership plans",
            description = "Retrieves a list of all available membership plans.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved membership plans")
            }
    )
    @GetMapping("/plans")
    public ResponseEntity<List<MembershipPlanDto>> getAllPlans() {
        return ResponseEntity.ok(membershipService.getAllPlans());
    }

    @Operation(
            summary = "Subscribe to a plan",
            description = "Subscribes a user to a specific membership plan.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully subscribed"),
                    @ApiResponse(responseCode = "404", description = "User or plan not found")
            }
    )
    @PostMapping("/subscribe/{userId}/{planId}")
    public ResponseEntity<ActiveMembershipDto> subscribeToPlan(
            @PathVariable UUID userId,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok(membershipService.subscribeUserToPlan(userId, planId));
    }

    @Operation(
            summary = "Get user memberships",
            description = "Retrieves all active and past memberships for a specific user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved memberships")
            }
    )
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ActiveMembershipDto>> getUserMemberships(@PathVariable UUID userId) {
        return ResponseEntity.ok(membershipService.getUserMemberships(userId));
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<ActiveMembershipDto> getCurrentMembership(@PathVariable UUID userId) {
        ActiveMembershipDto membership = membershipService.getCurrentMembership(userId);
        if (membership == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(membership);
    }
}
