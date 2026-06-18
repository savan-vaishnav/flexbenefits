package com.flexbenefits.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateBenefitPlanRequest(
        @NotBlank String name,
        @NotNull String type,
        String description,
        @NotNull String coverageTier,
        @Positive BigDecimal monthlyPremium,
        @Positive BigDecimal deductible,
        @Positive BigDecimal maxCoverage,
        Integer planYear
) {}

