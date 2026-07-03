package com.fitcore.gym.dto.dashboard;

import com.fitcore.gym.dto.notification.NotificationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardOverviewDto {
    private List<StatCardDto> dashboardCards;
    private List<ChartPointDto> weightProgress;
    private List<ChartPointDto> workoutCompletion;
    private List<ChartPointDto> attendanceChart;
    private List<UpcomingSessionDto> upcomingSessions;
    private List<ActivityDto> recentActivities;
    private List<NotificationDto> notifications;
}
