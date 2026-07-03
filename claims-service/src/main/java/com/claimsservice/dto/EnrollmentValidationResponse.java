package com.claimsservice.dto;

import java.util.UUID;

/**
 * Response from benefits-service enrollment validation endpoint.
 */
public record EnrollmentValidationResponse(
        UUID enrollmentId,
        UUID tenantId,
        UUID employeeId,
        UUID benefitPlanId,
        String status,
        boolean valid
) {}

