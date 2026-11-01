package com.fitcore.gym.service;

import com.fitcore.gym.domain.GymStatus;
import com.fitcore.gym.dto.auth.*;
import com.fitcore.gym.dto.user.SessionUserDto;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.ForbiddenException;
import com.fitcore.gym.repository.UserIdentityRepository;
import com.fitcore.gym.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserIdentityRepository identities;
    private final com.fitcore.gym.repository.GymRepository gyms;

    private final OnboardingService onboarding;

    public AuthResponse register(RegisterRequest request) {
        return onboarding.accept(request);
    }

    public AuthResponse login(AuthRequest request) {
        var authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getEmail(), request.getPassword()));
        User user = (User) authentication.getPrincipal();
        var gym = requireGym(user);
        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .user(SessionUserDto.from(user, gym))
                .build();
    }

    public SessionUserDto me() {
        var actor = TenantContext.current();
        User user =
                identities
                        .findById(actor.userId())
                        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        var gym = requireGym(user);
        return SessionUserDto.from(user, gym);
    }

    private com.fitcore.gym.entity.Gym requireGym(User user) {
        if (user.getGymId() == null) throw new ForbiddenException("GYM_ASSIGNMENT_REQUIRED");
        var gym =
                gyms.findById(user.getGymId())
                        .orElseThrow(() -> new ForbiddenException("GYM_ASSIGNMENT_REQUIRED"));
        if (gym.getStatus() != GymStatus.ACTIVE) throw new ForbiddenException("GYM_UNAVAILABLE");
        return gym;
    }

    public void requestPasswordReset(String email) {
        /* Generic accepted placeholder: no identity disclosure. */
    }
}
