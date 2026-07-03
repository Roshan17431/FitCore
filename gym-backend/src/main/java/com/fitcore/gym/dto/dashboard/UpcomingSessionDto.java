package com.fitcore.gym.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpcomingSessionDto {
    private Long id;
    private String focus;
    private String trainer;
    private LocalDate date;
    private String time;
}
