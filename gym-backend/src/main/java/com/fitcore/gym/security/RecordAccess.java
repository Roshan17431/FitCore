package com.fitcore.gym.security;

import com.fitcore.gym.exception.*;
import com.fitcore.gym.repository.AttendanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("recordAccess")
@RequiredArgsConstructor
public class RecordAccess {
    private final TenantAccess access;
    private final AttendanceRepository attendanceRepository;

    public boolean user(Authentication authentication, UUID userId) {
        access.requireUser(userId);
        return true;
    }

    public boolean attendance(Authentication authentication, Long id) {
        var row =
                attendanceRepository
                        .findByIdAndGymId(id, access.gym())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Attendance record not found"));
        access.requireUser(row.getUser().getId());
        return true;
    }
}
