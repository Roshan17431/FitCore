package com.fitcore.gym.dto.membership;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MembershipPlanDto {
    private Long id;
    
    @NotBlank(message = "Plan name is required")
    @Size(min = 3, max = 50, message = "Plan name must be between 3 and 50 characters")
    private String name;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    @NotNull(message = "Duration in months is required")
    @Positive(message = "Duration must be positive")
    private Integer durationMonths;
    
    private List<String> features;
    private Boolean recommended;
}
