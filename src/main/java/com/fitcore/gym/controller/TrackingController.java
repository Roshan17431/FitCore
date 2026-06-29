package com.fitcore.gym.controller;

import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.tracking.ProgressMetricDto;
import com.fitcore.gym.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @PostMapping("/progress/{userId}")
    public ResponseEntity<ProgressMetricDto> logProgress(
            @PathVariable UUID userId,
            @RequestBody ProgressMetricDto request
    ) {
        return ResponseEntity.ok(trackingService.logProgress(userId, request));
    }

    @GetMapping("/progress/{userId}")
    public ResponseEntity<List<ProgressMetricDto>> getUserProgress(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.getUserProgress(userId));
    }

    @PostMapping("/attendance/check-in/{userId}")
    public ResponseEntity<AttendanceDto> checkIn(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.checkIn(userId));
    }

    @PutMapping("/attendance/check-out/{attendanceId}")
    public ResponseEntity<AttendanceDto> checkOut(@PathVariable Long attendanceId) {
        return ResponseEntity.ok(trackingService.checkOut(attendanceId));
    }

    @GetMapping("/attendance/{userId}")
    public ResponseEntity<List<AttendanceDto>> getUserAttendance(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.getUserAttendance(userId));
    }
}
