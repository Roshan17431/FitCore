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

    @GetMapping
    public OnboardingService.GymView view() {
        return onboarding.view();
    }

    @PatchMapping
    public OnboardingService.GymView update(@Valid @RequestBody GymUpdateRequest r) {
        return onboarding.update(r);
    }

    @PostMapping("/invitations")
    public ResponseEntity<OnboardingService.IssuedInvitation> invite(
            @Valid @RequestBody InvitationRequest r) {
        return ResponseEntity.status(201).body(onboarding.invite(r));
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
