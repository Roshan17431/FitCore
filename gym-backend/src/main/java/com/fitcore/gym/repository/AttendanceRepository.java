package com.fitcore.gym.repository;

import com.fitcore.gym.entity.Attendance;
import com.fitcore.gym.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByUserOrderByCheckInTimeDesc(User user);
}
