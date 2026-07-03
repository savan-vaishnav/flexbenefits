package com.claimsservice.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event consumed from Kafka when an enrollment status changes.
 * Duplicated from benefits-service (microservice independence — no shared libraries).
 */
public record EnrollmentEvent(
        UUID enrollmentId,
        UUID tenantId,
        UUID employeeId,
        UUID benefitPlanId,
        String status,
        LocalDateTime timestamp
) {}

