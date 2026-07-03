package com.fitcore.gym.controller;

import com.fitcore.gym.dto.admin.AdminDashboardDto;
import com.fitcore.gym.dto.admin.AdminUserDto;
import com.fitcore.gym.dto.admin.TrainerUpsertRequest;
import com.fitcore.gym.dto.admin.UserUpdateRequest;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.service.AdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminUserDto> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateUser(userId, request));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        adminService.disableUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trainers")
    public ResponseEntity<TrainerProfileDto> createTrainer(
            @Valid @RequestBody TrainerUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTrainer(request));
    }

    @PutMapping("/trainers/{trainerId}")
    public ResponseEntity<TrainerProfileDto> updateTrainer(
            @PathVariable Long trainerId,
            @Valid @RequestBody TrainerUpsertRequest request
    ) {
        return ResponseEntity.ok(adminService.updateTrainer(trainerId, request));
    }

    @DeleteMapping("/trainers/{trainerId}")
    public ResponseEntity<Void> deleteTrainer(@PathVariable Long trainerId) {
        adminService.disableTrainer(trainerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/membership-plans")
    public ResponseEntity<MembershipPlanDto> createMembershipPlan(
            @Valid @RequestBody MembershipPlanDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createMembershipPlan(request));
    }

    @PutMapping("/membership-plans/{planId}")
    public ResponseEntity<MembershipPlanDto> updateMembershipPlan(
            @PathVariable Long planId,
            @Valid @RequestBody MembershipPlanDto request
    ) {
        return ResponseEntity.ok(adminService.updateMembershipPlan(planId, request));
    }

    @DeleteMapping("/membership-plans/{planId}")
    public ResponseEntity<Void> deleteMembershipPlan(@PathVariable Long planId) {
        adminService.deleteMembershipPlan(planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceDto>> getAttendance() {
        return ResponseEntity.ok(adminService.getAttendance());
    }
}
