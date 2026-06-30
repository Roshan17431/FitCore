package com.fitcore.gym.controller;

import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.tracking.ProgressMetricDto;
import com.fitcore.gym.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Tag(name = "Tracking", description = "Endpoints for managing attendance and progress metrics")
@SecurityRequirement(name = "Bearer Authentication")
public class TrackingController {

    private final TrackingService trackingService;

    @Operation(
            summary = "Log progress metric",
            description = "Saves a new progress metric (weight, body fat, measurements) for a user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully logged progress"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/progress/{userId}")
    public ResponseEntity<ProgressMetricDto> logProgress(
            @PathVariable UUID userId,
            @Valid @RequestBody ProgressMetricDto request
    ) {
        return ResponseEntity.ok(trackingService.logProgress(userId, request));
    }

    @Operation(
            summary = "Get user progress",
            description = "Retrieves all progress metrics for a specific user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved progress metrics")
            }
    )
    @GetMapping("/progress/{userId}")
    public ResponseEntity<List<ProgressMetricDto>> getUserProgress(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.getUserProgress(userId));
    }

    @Operation(
            summary = "Check in to gym",
            description = "Logs a new attendance check-in for the user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully checked in"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/attendance/check-in/{userId}")
    public ResponseEntity<AttendanceDto> checkIn(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.checkIn(userId));
    }

    @Operation(
            summary = "Check out of gym",
            description = "Updates an existing attendance record with a check-out time.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully checked out"),
                    @ApiResponse(responseCode = "404", description = "Attendance record not found")
            }
    )
    @PutMapping("/attendance/check-out/{attendanceId}")
    public ResponseEntity<AttendanceDto> checkOut(@PathVariable Long attendanceId) {
        return ResponseEntity.ok(trackingService.checkOut(attendanceId));
    }

    @Operation(
            summary = "Get user attendance",
            description = "Retrieves the attendance history for a specific user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved attendance history")
            }
    )
    @GetMapping("/attendance/{userId}")
    public ResponseEntity<List<AttendanceDto>> getUserAttendance(@PathVariable UUID userId) {
        return ResponseEntity.ok(trackingService.getUserAttendance(userId));
    }
}
