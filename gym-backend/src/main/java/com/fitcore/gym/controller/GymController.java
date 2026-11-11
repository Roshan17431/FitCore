package com.fitcore.gym.controller;

import com.fitcore.gym.dto.gym.*;
import com.fitcore.gym.service.OnboardingService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/gym")
@RequiredArgsConstructor
public class GymController {
    private final OnboardingService onboarding;
    @org.springframework.beans.factory.annotation.Value("${fitcore.browser.spike-enabled:false}") private boolean cookieMode;
    @org.springframework.beans.factory.annotation.Value("${fitcore.security-flows.demo-enabled:false}") private boolean securityFlows;
    @org.springframework.beans.factory.annotation.Value("${fitcore.email.outbox.enabled:false}") private boolean queued;

    @GetMapping
    public OnboardingService.GymView view() {
        return onboarding.view();
    }

    @PatchMapping
    public OnboardingService.GymView update(@Valid @RequestBody GymUpdateRequest r) {
        return onboarding.update(r);
    }

    @PostMapping("/invitations")
    public ResponseEntity<?> invite(
            @Valid @RequestBody InvitationRequest r) {
        if(cookieMode && (!securityFlows || !queued))throw new com.fitcore.gym.exception.ForbiddenException("SECURITY_ONBOARDING_DISABLED");
        var issued=onboarding.invite(r);
        if(cookieMode)return ResponseEntity.status(201).body(Map.of("id",issued.id(),"role",issued.role(),"expiresAt",issued.expiresAt(),"deliveryStatus","QUEUED"));
        return ResponseEntity.status(201).body(issued);
    }

    @GetMapping("/invitations")
    public List<OnboardingService.InvitationView> list() {
        return onboarding.list();
    }

    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        onboarding.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
