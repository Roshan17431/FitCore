package com.fitcore.gym.dto.gym;

import jakarta.validation.constraints.*;

public record OwnerRegistrationRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
                @Size(min = 12, max = 64)
                @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*$")
                String password,
        @NotBlank @Size(max = 100) String gymName,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,62}") String slug) {}
