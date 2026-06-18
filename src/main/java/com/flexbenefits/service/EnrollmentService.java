package com.flexbenefits.service;

import com.flexbenefits.dto.CreateEnrollmentRequest;
import com.flexbenefits.dto.EnrollmentResponse;
import com.flexbenefits.entity.BenefitPlan;
import com.flexbenefits.entity.Employee;
import com.flexbenefits.entity.Enrollment;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.EnrollmentMapper;
import com.flexbenefits.repository.BenefitPlanRepository;
import com.flexbenefits.repository.EmployeeRepository;
import com.flexbenefits.repository.EnrollmentRepository;
import com.flexbenefits.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final BenefitPlanRepository benefitPlanRepository;
    private final EnrollmentMapper enrollmentMapper;

    public EnrollmentResponse createEnrollment(UUID tenantId, CreateEnrollmentRequest request) {
        log.info("Creating enrollment for tenant: {}, employee: {}, plan: {}",
                tenantId, request.employeeId(), request.benefitPlanId());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", request.employeeId()));

        BenefitPlan plan = benefitPlanRepository.findById(request.benefitPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPlan", request.benefitPlanId()));

        // Verify employee and plan belong to this tenant
        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new IllegalStateException("Employee does not belong to tenant: " + tenantId);
        }
        if (!plan.getTenant().getId().equals(tenantId)) {
            throw new IllegalStateException("BenefitPlan does not belong to tenant: " + tenantId);
        }

        // Check if employee is already enrolled in this plan (not cancelled)
        if (enrollmentRepository.existsByEmployeeIdAndBenefitPlanIdAndStatusNot(
                request.employeeId(), request.benefitPlanId(), EnrollmentStatus.CANCELLED)) {
            throw new IllegalStateException("Employee is already enrolled in this plan");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setTenant(tenant);
        enrollment.setEmployee(employee);
        enrollment.setBenefitPlan(plan);
        enrollment.setStatus(EnrollmentStatus.PENDING);
        enrollment.setEnrollmentDate(request.enrollmentDate());
        enrollment.setEffectiveDate(request.effectiveDate());

        Enrollment saved = enrollmentRepository.save(enrollment);
        log.info("Enrollment created: {} for employee: {}", saved.getId(), request.employeeId());
        return enrollmentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getEnrollments(UUID tenantId) {
        List<Enrollment> enrollments = enrollmentRepository.findByTenantId(tenantId);
        return enrollmentMapper.toResponseList(enrollments);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getEnrollmentById(UUID tenantId, UUID enrollmentId) {
        Enrollment enrollment = findEnrollmentByTenantAndId(tenantId, enrollmentId);
        return enrollmentMapper.toResponse(enrollment);
    }

    public void cancelEnrollment(UUID tenantId, UUID enrollmentId) {
        Enrollment enrollment = findEnrollmentByTenantAndId(tenantId, enrollmentId);

        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new IllegalStateException("Enrollment is already cancelled");
        }

        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setTerminationDate(LocalDate.now());
        enrollmentRepository.save(enrollment);
        log.info("Enrollment cancelled: {} for tenant: {}", enrollmentId, tenantId);
    }

    // --- Private helpers ---

    private Enrollment findEnrollmentByTenantAndId(UUID tenantId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

        if (!enrollment.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Enrollment", enrollmentId);
        }

        return enrollment;
    }
}

