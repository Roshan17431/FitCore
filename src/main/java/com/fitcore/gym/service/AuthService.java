package com.fitcore.gym.service;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.dto.auth.AuthRequest;
import com.fitcore.gym.dto.auth.AuthResponse;
import com.fitcore.gym.dto.auth.RegisterRequest;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    // PasswordEncoder would be injected here when Spring Security is configured

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                // In a real app, hash the password
                .passwordHash(request.getPassword())
                .role(Role.MEMBER)
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .token("dummy-jwt-token")
                .user(userService.mapToDto(user))
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Verify password
        if (!user.getPasswordHash().equals(request.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return AuthResponse.builder()
                .token("dummy-jwt-token")
                .user(userService.mapToDto(user))
                .build();
    }
}
