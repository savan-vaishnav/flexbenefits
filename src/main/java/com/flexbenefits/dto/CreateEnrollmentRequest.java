package com.flexbenefits.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotNull UUID employeeId,
        @NotNull UUID benefitPlanId,
        @NotNull LocalDate enrollmentDate,
        LocalDate effectiveDate
) {}

