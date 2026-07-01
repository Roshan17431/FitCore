package com.fitcore.gym.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProgressMetricDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private LocalDate logDate;
    
    @Positive(message = "Weight must be positive")
    private Double weight;
    
    @Positive(message = "Body fat percentage must be positive")
    private Double bodyFatPercentage;
    
    @Positive(message = "Chest measurement must be positive")
    private Double chest;
    
    @Positive(message = "Waist measurement must be positive")
    private Double waist;
    
    @Positive(message = "Arms measurement must be positive")
    private Double arms;
    
    @Positive(message = "Legs measurement must be positive")
    private Double legs;
}
