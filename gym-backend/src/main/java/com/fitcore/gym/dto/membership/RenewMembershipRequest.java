package com.fitcore.gym.dto.membership;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RenewMembershipRequest(@NotNull LocalDate expectedEndDate) {}
