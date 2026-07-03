package com.fitcore.gym.dto.admin;

import com.fitcore.gym.dto.dashboard.ChartPointDto;
import com.fitcore.gym.dto.dashboard.StatCardDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminDashboardDto {
    private List<StatCardDto> adminCards;
    private List<ChartPointDto> membershipGrowth;
    private List<ChartPointDto> revenueAnalytics;
    private List<ChartPointDto> adminAttendanceAnalytics;
    private List<AdminUserDto> users;
    private List<TrainerProfileDto> trainers;
    private List<MembershipPlanDto> membershipPlans;
    private List<AttendanceDto> attendanceHistory;
}
