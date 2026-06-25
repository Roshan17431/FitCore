package com.fitcore.gym.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProgressMetricDto {
    private Long id;
    private UUID userId;
    private LocalDate logDate;
    private Double weight;
    private Double bodyFatPercentage;
    private Double chest;
    private Double waist;
    private Double arms;
    private Double legs;
}
