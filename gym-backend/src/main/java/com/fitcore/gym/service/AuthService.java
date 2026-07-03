package com.fitcore.gym.service;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.dto.auth.AuthRequest;
import com.fitcore.gym.dto.auth.AuthResponse;
import com.fitcore.gym.dto.auth.RegisterRequest;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.DuplicateResourceException;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.MEMBER)
                .fitnessGoal(request.getFitnessGoal())
                .enabled(true)
                .build();

        userRepository.save(user);
        var jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(userService.mapToDto(user))
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        var jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(userService.mapToDto(user))
                .build();
    }

    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.existsByEmail(email.trim().toLowerCase());
    }
}
