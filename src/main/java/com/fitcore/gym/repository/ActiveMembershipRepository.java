package com.fitcore.gym.repository;

import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActiveMembershipRepository extends JpaRepository<ActiveMembership, Long> {
    List<ActiveMembership> findByUser(User user);
}
