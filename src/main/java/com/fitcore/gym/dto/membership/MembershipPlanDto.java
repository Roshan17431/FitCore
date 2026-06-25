package com.fitcore.gym.dto.membership;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MembershipPlanDto {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer durationMonths;
    private List<String> features;
    private Boolean recommended;
}
