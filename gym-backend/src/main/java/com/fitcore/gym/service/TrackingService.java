package com.fitcore.gym.service;

import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.tracking.ProgressMetricDto;
import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.ProgressMetric;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.AttendanceRepository;
import com.fitcore.gym.repository.ProgressMetricRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class TrackingService {
    private final TenantAccess tenantAccess;

    private final ProgressMetricRepository progressMetricRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public ProgressMetricDto logProgress(UUID userId, ProgressMetricDto request) {
        User user = tenantAccess.requireUser(userId);

        ProgressMetric metric =
                ProgressMetric.builder()
                        .gymId(user.getGymId())
                        .user(user)
                        .logDate(
                                request.getLogDate() != null
                                        ? request.getLogDate()
                                        : LocalDate.now())
                        .weight(request.getWeight())
                        .bodyFatPercentage(request.getBodyFatPercentage())
                        .chest(request.getChest())
                        .waist(request.getWaist())
                        .arms(request.getArms())
                        .legs(request.getLegs())
                        .build();

        return mapToDto(progressMetricRepository.save(metric));
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<ProgressMetricDto> getUserProgress(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        return progressMetricRepository
                .findByGymIdAndUserIdOrderByLogDateDesc(tenantAccess.gym(), user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public AttendanceDto checkIn(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        var openAttendance =
                attendanceRepository
                        .findFirstByGymIdAndUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                                tenantAccess.gym(), user.getId());
        if (openAttendance.isPresent()) {
            return mapToDto(openAttendance.get());
        }

        Attendance attendance =
                Attendance.builder()
                        .gymId(user.getGymId())
                        .user(user)
                        .checkInDate(LocalDate.now())
                        .checkInTime(LocalDateTime.now())
                        .build();

        return mapToDto(attendanceRepository.save(attendance));
    }

    @PreAuthorize("@recordAccess.attendance(authentication, #attendanceId)")
    public AttendanceDto checkOut(Long attendanceId) {
        Attendance attendance =
                attendanceRepository
                        .findByIdAndGymId(attendanceId, tenantAccess.gym())
                        .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        attendance.setCheckOutTime(LocalDateTime.now());

        return mapToDto(attendanceRepository.save(attendance));
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<AttendanceDto> getUserAttendance(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        return attendanceRepository
                .findByGymIdAndUserIdOrderByCheckInTimeDesc(tenantAccess.gym(), user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProgressMetricDto mapToDto(ProgressMetric metric) {
        tenantAccess.requireOwned(metric);
        return ProgressMetricDto.builder()
                .id(metric.getId())
                .userId(metric.getUser().getId())
                .logDate(metric.getLogDate())
                .weight(metric.getWeight())
                .bodyFatPercentage(metric.getBodyFatPercentage())
                .chest(metric.getChest())
                .waist(metric.getWaist())
                .arms(metric.getArms())
                .legs(metric.getLegs())
                .build();
    }

    public AttendanceDto mapToDto(Attendance attendance) {
        tenantAccess.requireOwned(attendance);
        return AttendanceDto.builder()
                .id(attendance.getId())
                .userId(attendance.getUser().getId())
                .checkInDate(attendance.getCheckInDate())
                .checkInTime(attendance.getCheckInTime())
                .checkOutTime(attendance.getCheckOutTime())
                .build();
    }
}
