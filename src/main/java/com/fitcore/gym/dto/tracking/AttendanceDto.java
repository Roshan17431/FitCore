package com.fitcore.gym.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttendanceDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @PastOrPresent(message = "Check-in date cannot be in the future")
    private LocalDate checkInDate;
    
    @PastOrPresent(message = "Check-in time cannot be in the future")
    private LocalDateTime checkInTime;
    
    private LocalDateTime checkOutTime;
}
