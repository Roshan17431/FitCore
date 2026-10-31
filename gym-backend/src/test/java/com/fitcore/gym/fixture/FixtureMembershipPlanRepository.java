package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.MembershipPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixtureMembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {}
