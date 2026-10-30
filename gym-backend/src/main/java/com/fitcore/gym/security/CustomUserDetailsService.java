package com.fitcore.gym.security;

import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        // The demo password is public. Block it even for pre-existing database
        // rows and already-issued tokens; removing SQL seeds alone is insufficient.
        if ((user.getRole() == Role.ADMIN || user.getRole() == Role.OWNER) && passwordEncoder.matches("Fitcore@123", user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }
}
