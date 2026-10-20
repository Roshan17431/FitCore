package com.fitcore.gym.dto.membership;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record UpgradeMembershipRequest(@NotNull @Positive Long membershipId,
                                       @NotNull LocalDate expectedEndDate) {}
