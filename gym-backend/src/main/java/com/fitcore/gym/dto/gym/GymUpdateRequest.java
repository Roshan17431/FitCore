package com.fitcore.gym.dto.gym;

import jakarta.validation.constraints.*;

public record GymUpdateRequest(@NotBlank @Size(max = 100) String name) {}
