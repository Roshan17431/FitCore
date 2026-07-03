package com.fitcore.gym.service;

import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.tracking.ProgressMetricDto;
import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.ProgressMetric;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.AttendanceRepository;
import com.fitcore.gym.repository.ProgressMetricRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final ProgressMetricRepository progressMetricRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public ProgressMetricDto logProgress(UUID userId, ProgressMetricDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ProgressMetric metric = ProgressMetric.builder()
                .user(user)
                .logDate(request.getLogDate() != null ? request.getLogDate() : LocalDate.now())
                .weight(request.getWeight())
                .bodyFatPercentage(request.getBodyFatPercentage())
                .chest(request.getChest())
                .waist(request.getWaist())
                .arms(request.getArms())
                .legs(request.getLegs())
                .build();

        return mapToDto(progressMetricRepository.save(metric));
    }

    public List<ProgressMetricDto> getUserProgress(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return progressMetricRepository.findByUserOrderByLogDateDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public AttendanceDto checkIn(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var openAttendance = attendanceRepository.findFirstByUserAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user);
        if (openAttendance.isPresent()) {
            return mapToDto(openAttendance.get());
        }

        Attendance attendance = Attendance.builder()
                .user(user)
                .checkInDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .build();

        return mapToDto(attendanceRepository.save(attendance));
    }

    public AttendanceDto checkOut(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        attendance.setCheckOutTime(LocalDateTime.now());

        return mapToDto(attendanceRepository.save(attendance));
    }

    public List<AttendanceDto> getUserAttendance(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return attendanceRepository.findByUserOrderByCheckInTimeDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProgressMetricDto mapToDto(ProgressMetric metric) {
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
        return AttendanceDto.builder()
                .id(attendance.getId())
                .userId(attendance.getUser().getId())
                .checkInDate(attendance.getCheckInDate())
                .checkInTime(attendance.getCheckInTime())
                .checkOutTime(attendance.getCheckOutTime())
                .build();
    }
}
