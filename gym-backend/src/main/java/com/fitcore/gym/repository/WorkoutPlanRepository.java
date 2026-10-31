package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface WorkoutPlanRepository extends TenantRepository<WorkoutPlan, Long> {}
