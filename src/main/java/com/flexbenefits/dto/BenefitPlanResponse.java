package com.flexbenefits.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BenefitPlanResponse(
        UUID id,
        UUID tenantId,
        String name,
        String type,
        String description,
        String coverageTier,
        BigDecimal monthlyPremium,
        BigDecimal deductible,
        BigDecimal maxCoverage,
        Integer planYear,
        boolean active,
        LocalDateTime createdAt
) {}

