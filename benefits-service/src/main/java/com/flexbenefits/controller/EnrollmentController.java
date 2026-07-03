package com.flexbenefits.controller;

import com.flexbenefits.config.TenantContext;
import com.flexbenefits.dto.CreateEnrollmentRequest;
import com.flexbenefits.dto.EnrollmentResponse;
import com.flexbenefits.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> createEnrollment(
            @Valid @RequestBody CreateEnrollmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        EnrollmentResponse response = enrollmentService.createEnrollment(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EnrollmentResponse>> getEnrollments() {
        UUID tenantId = TenantContext.getTenantId();
        List<EnrollmentResponse> enrollments = enrollmentService.getEnrollments(tenantId);
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentResponse> getEnrollmentById(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        EnrollmentResponse response = enrollmentService.getEnrollmentById(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelEnrollment(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        enrollmentService.cancelEnrollment(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}

