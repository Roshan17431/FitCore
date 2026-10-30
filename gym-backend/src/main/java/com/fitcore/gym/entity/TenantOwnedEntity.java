package com.fitcore.gym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

/** Nullable only in the V2 expansion/legacy regression model. V4 requires ownership.
 * There is no default tenant. Runtime reassignment is prohibited by V4 triggers. */
@MappedSuperclass @Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode
public abstract class TenantOwnedEntity {
    @Column(name="gym_id", updatable=false)
    private UUID gymId;

    public void setGymId(UUID value) {
        if (gymId != null && !gymId.equals(value))
            throw new IllegalStateException("Gym ownership cannot be reassigned");
        gymId = value;
    }
}
