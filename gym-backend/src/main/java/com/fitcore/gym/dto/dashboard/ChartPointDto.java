package com.fitcore.gym.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChartPointDto {
    private String label;
    private Double weight;
    private Double bodyFat;
    private Integer completed;
    private Integer visits;
    private Integer members;
    private Double revenue;
}
