package com.fitcore.gym.service;

import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.dto.dashboard.ActivityDto;
import com.fitcore.gym.dto.dashboard.ChartPointDto;
import com.fitcore.gym.dto.dashboard.DashboardOverviewDto;
import com.fitcore.gym.dto.dashboard.StatCardDto;
import com.fitcore.gym.dto.dashboard.UpcomingSessionDto;
import com.fitcore.gym.dto.membership.ActiveMembershipDto;
import com.fitcore.gym.dto.notification.NotificationDto;
import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.FoodLog;
import com.fitcore.gym.entity.Notification;
import com.fitcore.gym.entity.ProgressMetric;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.entity.WorkoutLog;
import com.fitcore.gym.repository.AttendanceRepository;
import com.fitcore.gym.repository.FoodLogRepository;
import com.fitcore.gym.repository.NotificationRepository;
import com.fitcore.gym.repository.ProgressMetricRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.repository.WorkoutExerciseLogRepository;
import com.fitcore.gym.repository.WorkoutLogRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class DashboardService {
    private final TenantAccess tenantAccess;

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMM d");

    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final Clock clock;
    private final WorkoutLogRepository workoutLogRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final FoodLogRepository foodLogRepository;
    private final AttendanceRepository attendanceRepository;
    private final ProgressMetricRepository progressMetricRepository;
    private final TrainerSessionRepository trainerSessionRepository;
    private final NotificationRepository notificationRepository;

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public DashboardOverviewDto getOverview(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        ActiveMembershipDto membership = membershipService.getCurrentMembership(userId);
        List<WorkoutLog> workouts =
                workoutLogRepository.findByGymIdAndUserIdOrderByDateDesc(
                        tenantAccess.gym(), user.getId());
        List<FoodLog> todayFoodLogs =
                foodLogRepository.findByGymIdAndUserIdAndLogDateOrderByLogTimeDesc(
                        tenantAccess.gym(), user.getId(), today);
        List<Attendance> attendance =
                attendanceRepository.findByGymIdAndUserIdOrderByCheckInTimeDesc(
                        tenantAccess.gym(), user.getId());
        ProgressMetric latestProgress =
                progressMetricRepository
                        .findFirstByGymIdAndUserIdOrderByLogDateDesc(
                                tenantAccess.gym(), user.getId())
                        .orElse(null);

        return DashboardOverviewDto.builder()
                .dashboardCards(
                        buildCards(
                                membership,
                                workouts,
                                todayFoodLogs,
                                attendance,
                                latestProgress,
                                today))
                .weightProgress(buildWeightProgress(user))
                .workoutCompletion(buildWorkoutCompletion(user, today))
                .attendanceChart(buildAttendanceChart(user, today))
                .upcomingSessions(buildUpcomingSessions(user, today))
                .recentActivities(buildRecentActivities(workouts, todayFoodLogs, attendance))
                .notifications(
                        notificationRepository
                                .findByGymIdAndUserIdOrderByCreatedAtDesc(
                                        tenantAccess.gym(), user.getId())
                                .stream()
                                .map(this::mapNotification)
                                .collect(Collectors.toList()))
                .build();
    }

    private List<StatCardDto> buildCards(
            ActiveMembershipDto membership,
            List<WorkoutLog> workouts,
            List<FoodLog> todayFoodLogs,
            List<Attendance> attendance,
            ProgressMetric latestProgress,
            LocalDate today) {
        int calories = todayFoodLogs.stream().mapToInt(log -> valueOrZero(log.getCalories())).sum();
        long monthVisits =
                attendance.stream()
                        .filter(item -> item.getCheckInDate().getMonth().equals(today.getMonth()))
                        .count();
        WorkoutLog todayWorkout =
                workouts.stream()
                        .filter(log -> today.equals(log.getDate()))
                        .findFirst()
                        .orElse(null);

        return List.of(
                card(
                        "Membership Status",
                        membership == null ? "No Plan" : membership.getStatus().name(),
                        membership == null
                                ? "No active subscription"
                                : membership.getPlan().getName() + " plan",
                        membership == null ? "slate" : "emerald"),
                card(
                        "Today's Workout",
                        todayWorkout == null
                                ? "Not logged"
                                : workoutExerciseLogRepository
                                                .findByGymIdAndWorkoutLogId(
                                                        tenantAccess.gym(), todayWorkout.getId())
                                                .size()
                                        + " exercises",
                        todayWorkout == null
                                ? "No completed workout today"
                                : todayWorkout.getDurationMinutes() + " minutes",
                        todayWorkout == null ? "amber" : "emerald"),
                card(
                        "Calories Consumed",
                        calories + " kcal",
                        todayFoodLogs.size() + " food entries",
                        "amber"),
                card("Attendance Summary", monthVisits + " visits", "This month", "sky"),
                card(
                        "Progress Summary",
                        latestProgress == null || latestProgress.getWeight() == null
                                ? "No weight"
                                : latestProgress.getWeight() + " kg",
                        latestProgress == null || latestProgress.getBodyFatPercentage() == null
                                ? "No body fat record"
                                : latestProgress.getBodyFatPercentage() + "% body fat",
                        "emerald"));
    }

    private List<ChartPointDto> buildWeightProgress(User user) {
        return progressMetricRepository
                .findByGymIdAndUserIdOrderByLogDateDesc(tenantAccess.gym(), user.getId())
                .stream()
                .sorted(Comparator.comparing(ProgressMetric::getLogDate))
                .map(
                        metric ->
                                ChartPointDto.builder()
                                        .label(metric.getLogDate().format(LABEL_FORMATTER))
                                        .weight(metric.getWeight())
                                        .bodyFat(metric.getBodyFatPercentage())
                                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartPointDto> buildWorkoutCompletion(User user, LocalDate today) {
        List<WorkoutLog> recent =
                workoutLogRepository.findByGymIdAndUserIdAndDateBetweenOrderByDateAsc(
                        tenantAccess.gym(), user.getId(), today.minusDays(6), today);
        return IntStream.rangeClosed(0, 6)
                .mapToObj(index -> today.minusDays(6L - index))
                .map(
                        date ->
                                ChartPointDto.builder()
                                        .label(date.getDayOfWeek().name().substring(0, 3))
                                        .completed(
                                                recent.stream()
                                                                .anyMatch(
                                                                        log ->
                                                                                date.equals(
                                                                                        log
                                                                                                .getDate()))
                                                        ? 100
                                                        : 0)
                                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartPointDto> buildAttendanceChart(User user, LocalDate today) {
        List<Attendance> attendance =
                attendanceRepository.findByGymIdAndUserIdOrderByCheckInTimeDesc(
                        tenantAccess.gym(), user.getId());
        return IntStream.rangeClosed(0, 6)
                .mapToObj(index -> today.minusDays(6L - index))
                .map(
                        date ->
                                ChartPointDto.builder()
                                        .label(date.getDayOfWeek().name().substring(0, 3))
                                        .visits(
                                                (int)
                                                        attendance.stream()
                                                                .filter(
                                                                        item ->
                                                                                date.equals(
                                                                                        item
                                                                                                .getCheckInDate()))
                                                                .count())
                                        .build())
                .collect(Collectors.toList());
    }

    private List<UpcomingSessionDto> buildUpcomingSessions(User user, LocalDate today) {
        return trainerSessionRepository
                .findByGymIdAndMemberIdOrderBySessionDateAscStartTimeAsc(
                        tenantAccess.gym(), user.getId())
                .stream()
                .filter(session -> session.getStatus() == SessionStatus.SCHEDULED)
                .filter(session -> !session.getSessionDate().isBefore(today))
                .limit(3)
                .map(this::mapUpcomingSession)
                .collect(Collectors.toList());
    }

    private List<ActivityDto> buildRecentActivities(
            List<WorkoutLog> workouts, List<FoodLog> todayFoodLogs, List<Attendance> attendance) {
        Stream<ActivityDto> workoutActivities =
                workouts.stream()
                        .limit(2)
                        .map(
                                log ->
                                        ActivityDto.builder()
                                                .id("workout-" + log.getId())
                                                .title("Workout logged")
                                                .meta(
                                                        log.getNotes() == null
                                                                ? "Training session"
                                                                : log.getNotes())
                                                .time(log.getDate().format(LABEL_FORMATTER))
                                                .build());

        Stream<ActivityDto> foodActivities =
                todayFoodLogs.stream()
                        .limit(2)
                        .map(
                                log ->
                                        ActivityDto.builder()
                                                .id("food-" + log.getId())
                                                .title("Food logged")
                                                .meta(
                                                        log.getMealName()
                                                                + " - "
                                                                + valueOrZero(log.getCalories())
                                                                + " kcal")
                                                .time(formatTime(log.getLogTime()))
                                                .build());

        Stream<ActivityDto> attendanceActivities =
                attendance.stream()
                        .limit(2)
                        .map(
                                log ->
                                        ActivityDto.builder()
                                                .id("attendance-" + log.getId())
                                                .title("Gym visit recorded")
                                                .meta(
                                                        "Checked in at "
                                                                + formatTime(
                                                                        log.getCheckInTime()
                                                                                .toLocalTime()))
                                                .time(log.getCheckInDate().format(LABEL_FORMATTER))
                                                .build());

        return Stream.of(workoutActivities, foodActivities, attendanceActivities)
                .flatMap(stream -> stream)
                .limit(6)
                .collect(Collectors.toList());
    }

    private UpcomingSessionDto mapUpcomingSession(TrainerSession session) {
        tenantAccess.requireOwned(session);
        return UpcomingSessionDto.builder()
                .id(session.getId())
                .focus(session.getTrainer().getSpecialization())
                .trainer(session.getTrainer().getUser().getFullName())
                .date(session.getSessionDate())
                .time(formatTime(session.getStartTime()))
                .build();
    }

    private NotificationDto mapNotification(Notification notification) {
        tenantAccess.requireOwned(notification);
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .type(notification.getType())
                .readFlag(notification.getReadFlag())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private StatCardDto card(String title, String value, String detail, String tone) {
        return StatCardDto.builder().title(title).value(value).detail(detail).tone(tone).build();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatTime(LocalTime time) {
        return time == null ? "--" : time.toString();
    }
}
