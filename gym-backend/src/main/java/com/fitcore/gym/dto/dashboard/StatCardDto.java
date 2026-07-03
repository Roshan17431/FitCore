package com.fitcore.gym.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatCardDto {
    private String title;
    private String value;
    private String detail;
    private String trend;
    private String tone;
}
