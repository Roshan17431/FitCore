package com.fitcore.gym.controller;

import com.fitcore.gym.dto.dashboard.DashboardOverviewDto;
import com.fitcore.gym.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated member dashboard data")
@SecurityRequirement(name = "Bearer Authentication")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{userId}")
    public ResponseEntity<DashboardOverviewDto> getOverview(@PathVariable UUID userId) {
        return ResponseEntity.ok(dashboardService.getOverview(userId));
    }
}
