package com.flexbenefits.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        UUID benefitPlanId,
        String status,
        LocalDate enrollmentDate,
        LocalDate effectiveDate,
        LocalDate terminationDate,
        LocalDateTime createdAt
) {}

