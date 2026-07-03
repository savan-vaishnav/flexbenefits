package com.flexbenefits.dto;

import java.util.UUID;

public record EnrollmentValidationResponse(
        UUID enrollmentId,
        UUID tenantId,
        UUID employeeId,
        UUID benefitPlanId,
        String status,
        boolean valid
) {}

