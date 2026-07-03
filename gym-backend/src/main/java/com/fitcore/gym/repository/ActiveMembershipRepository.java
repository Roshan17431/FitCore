package com.fitcore.gym.repository;

import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.domain.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveMembershipRepository extends JpaRepository<ActiveMembership, Long> {
    List<ActiveMembership> findByUser(User user);
    List<ActiveMembership> findByUserOrderByStartDateDesc(User user);
    List<ActiveMembership> findByStatus(MembershipStatus status);
    Optional<ActiveMembership> findFirstByUserOrderByStartDateDesc(User user);
    long countByStatus(MembershipStatus status);
    boolean existsByPlan(MembershipPlan plan);
}
