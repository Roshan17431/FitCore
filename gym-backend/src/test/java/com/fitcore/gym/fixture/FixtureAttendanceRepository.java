package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixtureAttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByUserOrderByCheckInTimeDesc(User user);

    List<Attendance> findAllByOrderByCheckInTimeDesc();

    List<Attendance> findByCheckInDateBetween(LocalDate start, LocalDate end);

    long countByCheckInDateBetween(LocalDate start, LocalDate end);

    Optional<Attendance> findFirstByUserAndCheckOutTimeIsNullOrderByCheckInTimeDesc(User user);
}
