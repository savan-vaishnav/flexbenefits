package com.flexbenefits.controller;

import com.flexbenefits.dto.EnrollmentValidationResponse;
import com.flexbenefits.entity.Enrollment;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import com.flexbenefits.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal API for inter-service communication.
 * Called by claims-service to validate enrollment before creating claims.
 */
@RestController
@RequestMapping("/api/internal/v1")
@RequiredArgsConstructor
@Slf4j
public class InternalApiController {

    private final EnrollmentRepository enrollmentRepository;

    @GetMapping("/enrollments/{enrollmentId}/validate")
    public ResponseEntity<EnrollmentValidationResponse> validateEnrollment(
            @PathVariable UUID enrollmentId,
            @RequestParam UUID tenantId) {

        log.info("Validating enrollment: {} for tenant: {}", enrollmentId, tenantId);

        return enrollmentRepository.findById(enrollmentId)
                .filter(enrollment -> enrollment.getTenant().getId().equals(tenantId))
                .map(enrollment -> {
                    boolean isValid = enrollment.getStatus() == EnrollmentStatus.ACTIVE
                            || enrollment.getStatus() == EnrollmentStatus.PENDING;
                    return ResponseEntity.ok(new EnrollmentValidationResponse(
                            enrollment.getId(),
                            enrollment.getTenant().getId(),
                            enrollment.getEmployee().getId(),
                            enrollment.getBenefitPlan().getId(),
                            enrollment.getStatus().name(),
                            isValid
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

