package com.flexbenefits.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published to Kafka when an enrollment status changes.
 * Consumed by claims-service to auto-reject related claims on cancellation.
 */
public record EnrollmentEvent(
        UUID enrollmentId,
        UUID tenantId,
        UUID employeeId,
        UUID benefitPlanId,
        String status,
        LocalDateTime timestamp
) {}

