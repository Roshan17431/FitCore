package com.fitcore.gym.service;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.dto.admin.AdminDashboardDto;
import com.fitcore.gym.dto.admin.AdminUserDto;
import com.fitcore.gym.dto.admin.TrainerUpsertRequest;
import com.fitcore.gym.dto.admin.UserUpdateRequest;
import com.fitcore.gym.dto.dashboard.ChartPointDto;
import com.fitcore.gym.dto.dashboard.StatCardDto;
import com.fitcore.gym.dto.membership.MembershipPlanDto;
import com.fitcore.gym.dto.tracking.AttendanceDto;
import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.BadRequestException;
import com.fitcore.gym.exception.ResourceNotFoundException;
import com.fitcore.gym.repository.ActiveMembershipRepository;
import com.fitcore.gym.repository.AttendanceRepository;
import com.fitcore.gym.repository.MembershipPlanRepository;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class AdminService {
    private final TenantAccess tenantAccess;

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM");

    private final UserRepository userRepository;
    private final com.fitcore.gym.security.IdentityUniqueness identityUniqueness;
    private final com.fitcore.gym.repository.GymRepository gyms;
    private final ActiveMembershipRepository activeMembershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final TrainerProfileRepository trainerProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipService membershipService;
    private final TrainerService trainerService;
    private final TrackingService trackingService;
    private final Clock clock;

    public AdminDashboardDto getDashboard() {
        List<AdminUserDto> users = getUsers();
        List<TrainerProfileDto> trainers =
                trainerProfileRepository.findAllByGymId(tenantAccess.gym()).stream()
                        .map(trainerService::mapToDto)
                        .collect(Collectors.toList());
        List<MembershipPlanDto> plans =
                membershipPlanRepository.findAllByGymId(tenantAccess.gym()).stream()
                        .map(membershipService::mapToDto)
                        .collect(Collectors.toList());
        List<AttendanceDto> attendance = getAttendance();

        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var currentByUser =
                activeMembershipRepository
                        .findByGymIdAndStatus(tenantAccess.gym(), MembershipStatus.ACTIVE)
                        .stream()
                        .filter(item -> item.isCurrentOn(date))
                        .collect(Collectors.groupingBy(item -> item.getUser().getId()));
        List<ActiveMembership> currentMemberships =
                currentByUser.values().stream()
                        .filter(items -> items.size() == 1)
                        .map(items -> items.get(0))
                        .toList();
        long needsReview =
                currentByUser.values().stream().filter(items -> items.size() > 1).count();
        BigDecimal revenue =
                currentMemberships.stream()
                        .map(item -> item.getPlan().getPrice())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardDto.builder()
                .adminCards(
                        List.of(
                                card(
                                        "Total Members",
                                        String.valueOf(
                                                userRepository.countByGymIdAndRole(
                                                        tenantAccess.gym(), Role.MEMBER)),
                                        "Registered members",
                                        "sky"),
                                card(
                                        "Active Members",
                                        String.valueOf(currentMemberships.size()),
                                        needsReview == 0
                                                ? "Active subscriptions"
                                                : needsReview + " users need membership review",
                                        "emerald"),
                                card(
                                        "Trainers",
                                        String.valueOf(
                                                userRepository.countByGymIdAndRole(
                                                        tenantAccess.gym(), Role.TRAINER)),
                                        "Trainer accounts",
                                        "amber"),
                                card(
                                        "Revenue",
                                        "$" + revenue.intValue(),
                                        "Active subscriptions",
                                        "emerald")))
                .membershipGrowth(buildMembershipGrowth())
                .revenueAnalytics(buildRevenueAnalytics())
                .adminAttendanceAnalytics(buildAttendanceAnalytics())
                .users(users)
                .trainers(trainers)
                .membershipPlans(plans)
                .attendanceHistory(attendance)
                .build();
    }

    public List<AdminUserDto> getUsers() {
        return userRepository.findAllByGymId(tenantAccess.gym()).stream()
                .map(this::mapUser)
                .collect(Collectors.toList());
    }

    public AdminUserDto updateUser(UUID userId, UserUpdateRequest request) {
        if (request.getRole() == null) {
            throw new BadRequestException(
                    "OWNER appointment requires a separate ownership workflow");
        }
        User user = managedUser(userId);
        identityUniqueness.requireAvailable(request.getEmail(), userId);

        tenantAccess.assign(request.getRole().role());
        user.setAuthzVersion(user.getAuthzVersion() + 1);
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole().role());
        user.setFitnessGoal(request.getFitnessGoal());
        user.setEnabled(request.getEnabled() == null || request.getEnabled());
        return mapUser(userRepository.save(user));
    }

    public void disableUser(UUID userId) {
        User user = managedUser(userId);
        user.setAuthzVersion(user.getAuthzVersion() + 1);
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Transactional
    public TrainerProfileDto createTrainer(TrainerUpsertRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required for new trainer accounts");
        }
        identityUniqueness.requireAvailable(request.getEmail(), null);

        User trainerUser =
                User.builder()
                        .gymId(managedGymId())
                        .fullName(request.getFullName())
                        .email(request.getEmail())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .role(Role.TRAINER)
                        .fitnessGoal(request.getSpecialization())
                        .enabled(true)
                        .build();
        trainerUser = userRepository.save(trainerUser);

        TrainerProfile profile =
                TrainerProfile.builder()
                        .gymId(trainerUser.getGymId())
                        .user(trainerUser)
                        .specialization(request.getSpecialization())
                        .experienceYears(
                                request.getExperienceYears() == null
                                        ? 0
                                        : request.getExperienceYears())
                        .bio(request.getBio())
                        .build();

        return trainerService.mapToDto(trainerProfileRepository.save(profile));
    }

    public TrainerProfileDto updateTrainer(Long trainerId, TrainerUpsertRequest request) {
        TrainerProfile profile =
                trainerProfileRepository
                        .findByIdAndGymId(trainerId, tenantAccess.gym())
                        .orElseThrow(() -> new ResourceNotFoundException("Trainer not found"));
        User trainerUser = managedUser(profile.getUser().getId());
        trainerUser.setAuthzVersion(trainerUser.getAuthzVersion() + 1);

        identityUniqueness.requireAvailable(request.getEmail(), trainerUser.getId());

        trainerUser.setFullName(request.getFullName());
        trainerUser.setEmail(request.getEmail());
        trainerUser.setFitnessGoal(request.getSpecialization());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            trainerUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userRepository.save(trainerUser);

        profile.setSpecialization(request.getSpecialization());
        profile.setExperienceYears(
                request.getExperienceYears() == null ? 0 : request.getExperienceYears());
        profile.setBio(request.getBio());
        return trainerService.mapToDto(trainerProfileRepository.save(profile));
    }

    public void disableTrainer(Long trainerId) {
        TrainerProfile profile =
                trainerProfileRepository
                        .findByIdAndGymId(trainerId, tenantAccess.gym())
                        .orElseThrow(() -> new ResourceNotFoundException("Trainer not found"));
        User user = managedUser(profile.getUser().getId());
        user.setAuthzVersion(user.getAuthzVersion() + 1);
        user.setEnabled(false);
        userRepository.save(user);
    }

    public MembershipPlanDto createMembershipPlan(MembershipPlanDto request) {
        MembershipPlan plan =
                MembershipPlan.builder()
                        .gymId(managedGymId())
                        .name(request.getName())
                        .price(request.getPrice())
                        .durationMonths(request.getDurationMonths())
                        .features(request.getFeatures())
                        .recommended(Boolean.TRUE.equals(request.getRecommended()))
                        .build();
        return membershipService.mapToDto(membershipPlanRepository.save(plan));
    }

    public MembershipPlanDto updateMembershipPlan(Long planId, MembershipPlanDto request) {
        MembershipPlan plan =
                membershipPlanRepository
                        .findByIdAndGymId(planId, tenantAccess.gym())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Membership plan not found"));
        plan.setName(request.getName());
        plan.setPrice(request.getPrice());
        plan.setDurationMonths(request.getDurationMonths());
        plan.setFeatures(request.getFeatures());
        plan.setRecommended(Boolean.TRUE.equals(request.getRecommended()));
        return membershipService.mapToDto(membershipPlanRepository.save(plan));
    }

    public void deleteMembershipPlan(Long planId) {
        MembershipPlan plan =
                membershipPlanRepository
                        .findByIdAndGymId(planId, tenantAccess.gym())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Membership plan not found"));
        if (activeMembershipRepository.existsByGymIdAndPlanId(tenantAccess.gym(), plan.getId())) {
            throw new BadRequestException("Cannot delete a plan that has membership history");
        }
        membershipPlanRepository.delete(plan);
    }

    public List<AttendanceDto> getAttendance() {
        return attendanceRepository
                .findAllByGymIdOrderByCheckInTimeDesc(tenantAccess.gym())
                .stream()
                .map(trackingService::mapToDto)
                .collect(Collectors.toList());
    }

    private UUID managedGymId() {
        return tenantAccess.manager().gymId();
    }

    private User managedUser(UUID id) {
        UUID gym = managedGymId();
        gyms.findByIdForUpdate(gym)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found"));
        User target =
                userRepository
                        .findByIdAndGymIdForUpdate(id, gym)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        tenantAccess.manageTarget(target);
        return target;
    }

    private AdminUserDto mapUser(User user) {
        tenantAccess.requireOwned(user);
        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var current =
                activeMembershipRepository
                        .findByGymIdAndUserIdOrderByStartDateDesc(tenantAccess.gym(), user.getId())
                        .stream()
                        .filter(item -> item.isCurrentOn(date))
                        .toList();
        String membership =
                current.size() > 1
                        ? "Review required"
                        : current.isEmpty() ? "None" : current.get(0).getPlan().getName();
        return AdminUserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .fitnessGoal(user.getFitnessGoal())
                .membership(membership)
                .status(user.isEnabled() ? "Active" : "Inactive")
                .enabled(user.isEnabled())
                .build();
    }

    private List<ChartPointDto> buildMembershipGrowth() {
        List<User> members = userRepository.findByGymIdAndRole(tenantAccess.gym(), Role.MEMBER);
        YearMonth current = YearMonth.now();
        return IntStream.rangeClosed(0, 5)
                .mapToObj(index -> current.minusMonths(5L - index))
                .map(
                        month ->
                                ChartPointDto.builder()
                                        .label(month.format(MONTH_LABEL))
                                        .members(
                                                (int)
                                                        members.stream()
                                                                .filter(
                                                                        member ->
                                                                                member
                                                                                                .getCreatedAt()
                                                                                        != null)
                                                                .filter(
                                                                        member ->
                                                                                !member.getCreatedAt()
                                                                                        .toLocalDate()
                                                                                        .isAfter(
                                                                                                month
                                                                                                        .atEndOfMonth()))
                                                                .count())
                                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartPointDto> buildRevenueAnalytics() {
        YearMonth current = YearMonth.now();
        List<ActiveMembership> memberships =
                activeMembershipRepository.findAllByGymId(tenantAccess.gym());
        return IntStream.rangeClosed(0, 5)
                .mapToObj(index -> current.minusMonths(5L - index))
                .map(
                        month -> {
                            BigDecimal revenue =
                                    memberships.stream()
                                            .filter(
                                                    item ->
                                                            YearMonth.from(item.getStartDate())
                                                                    .equals(month))
                                            .map(item -> item.getPlan().getPrice())
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            return ChartPointDto.builder()
                                    .label(month.format(MONTH_LABEL))
                                    .revenue(revenue.doubleValue())
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    private List<ChartPointDto> buildAttendanceAnalytics() {
        LocalDate today = LocalDate.now();
        List<Attendance> attendance =
                attendanceRepository.findByGymIdAndCheckInDateBetween(
                        tenantAccess.gym(), today.minusDays(6), today);
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

    private StatCardDto card(String title, String value, String detail, String tone) {
        return StatCardDto.builder().title(title).value(value).detail(detail).tone(tone).build();
    }
}
