package com.fitcore.gym.dto.membership;

import com.fitcore.gym.domain.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveMembershipDto {
    private Long id;
    private UUID userId;
    private MembershipPlanDto plan;
    private LocalDate startDate;
    private LocalDate endDate;
    private MembershipStatus status;
}
