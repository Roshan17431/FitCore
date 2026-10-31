package com.fitcore.gym.security;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.UserIdentityRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserIdentityRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByEmail(username)
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "User not found with email: " + username));

        requireUsableCredentials(user);
        return user;
    }

    public User loadById(java.util.UUID id) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new UsernameNotFoundException("Invalid identity"));
        requireUsableCredentials(user);
        return user;
    }

    private void requireUsableCredentials(User user) {
        if (!user.isEnabled()) throw new BadCredentialsException("Invalid credentials");
        // The demo password is public. Block it even for pre-existing database
        // rows and already-issued tokens; removing SQL seeds alone is insufficient.
        if ((user.getRole() == Role.ADMIN || user.getRole() == Role.OWNER)
                && passwordEncoder.matches("Fitcore@123", user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}
