package com.fitcore.gym.dto.membership;

import com.fitcore.gym.domain.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveMembershipDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Membership plan is required")
    @Valid
    private MembershipPlanDto plan;
    
    private LocalDate startDate;
    private LocalDate endDate;
    private MembershipStatus status;
    private String effectiveStatus;
    private long remainingDays;
    private LocalDate serverDate;
    private String timeZone;
}
