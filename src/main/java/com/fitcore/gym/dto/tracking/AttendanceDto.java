package com.fitcore.gym.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttendanceDto {
    private Long id;
    private UUID userId;
    private LocalDate checkInDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
}
