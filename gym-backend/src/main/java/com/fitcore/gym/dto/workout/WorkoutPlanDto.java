package com.fitcore.gym.dto.workout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutPlanDto {
    private Long id;
    private String level;
    private String duration;
    private List<String> schedule;
    private List<String> exercises;
}
