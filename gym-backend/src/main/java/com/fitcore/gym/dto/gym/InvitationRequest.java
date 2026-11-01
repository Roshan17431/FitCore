package com.fitcore.gym.dto.gym;

import jakarta.validation.constraints.*;

public record InvitationRequest(
        @NotBlank @Email @Size(max = 254) String email, @NotNull InviteRole role) {
    public enum InviteRole {
        MEMBER,
        TRAINER
    }
}
