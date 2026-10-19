package com.fitcore.gym.security;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("recordAccess")
@RequiredArgsConstructor
public class RecordAccess {
    private final AttendanceRepository attendanceRepository;

    public boolean user(Authentication authentication, UUID userId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User caller) || !caller.isEnabled()) {
            return false;
        }
        return caller.getRole() == Role.ADMIN || caller.getId().equals(userId);
    }

    public boolean attendance(Authentication authentication, Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .map(record -> user(authentication, record.getUser().getId()))
                .orElse(false);
    }
}
