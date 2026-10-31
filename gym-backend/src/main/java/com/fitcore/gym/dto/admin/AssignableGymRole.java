package com.fitcore.gym.dto.admin;

import com.fitcore.gym.domain.Role;

/** OWNER and platform grants cannot enter the ordinary editing contract. */
public enum AssignableGymRole {
    MEMBER,
    TRAINER,
    ADMIN;

    public Role role() {
        return Role.valueOf(name());
    }
}
