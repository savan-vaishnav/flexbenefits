package com.flexbenefits.service;

import com.flexbenefits.dto.CreateEnrollmentRequest;
import com.flexbenefits.dto.EnrollmentResponse;
import com.flexbenefits.entity.*;
import com.flexbenefits.entity.enums.CoverageTier;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import com.flexbenefits.entity.enums.PlanType;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.EnrollmentMapper;
import com.flexbenefits.repository.BenefitPlanRepository;
import com.flexbenefits.repository.EmployeeRepository;
import com.flexbenefits.repository.EnrollmentRepository;
import com.flexbenefits.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService Unit Tests")
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private BenefitPlanRepository benefitPlanRepository;
    @Mock private EnrollmentMapper enrollmentMapper;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private UUID tenantId;
    private UUID employeeId;
    private UUID planId;
    private UUID enrollmentId;
    private Tenant tenant;
    private Employee employee;
    private BenefitPlan plan;
    private Enrollment enrollment;
    private EnrollmentResponse enrollmentResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        planId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Corp");
        tenant.setCode("ACME");

        employee = new Employee();
        employee.setId(employeeId);
        employee.setTenant(tenant);
        employee.setFirstName("John");
        employee.setLastName("Doe");

        plan = new BenefitPlan();
        plan.setId(planId);
        plan.setTenant(tenant);
        plan.setName("Gold Medical");
        plan.setType(PlanType.MEDICAL);
        plan.setCoverageTier(CoverageTier.EMPLOYEE_ONLY);

        enrollment = new Enrollment();
        enrollment.setId(enrollmentId);
        enrollment.setTenant(tenant);
        enrollment.setEmployee(employee);
        enrollment.setBenefitPlan(plan);
        enrollment.setStatus(EnrollmentStatus.PENDING);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 1, 1));
        enrollment.setEffectiveDate(LocalDate.of(2026, 1, 15));

        enrollmentResponse = new EnrollmentResponse(
                enrollmentId, tenantId, employeeId, planId,
                "PENDING", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15), null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("createEnrollment")
    class CreateEnrollmentTests {

        private CreateEnrollmentRequest request;

        @BeforeEach
        void setUp() {
            request = new CreateEnrollmentRequest(
                    employeeId, planId,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 15)
            );
        }

        @Test
        @DisplayName("should create enrollment successfully")
        void shouldCreateEnrollmentSuccessfully() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(enrollmentRepository.existsByEmployeeIdAndBenefitPlanIdAndStatusNot(
                    employeeId, planId, EnrollmentStatus.CANCELLED)).thenReturn(false);
            when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);
            when(enrollmentMapper.toResponse(enrollment)).thenReturn(enrollmentResponse);

            EnrollmentResponse result = enrollmentService.createEnrollment(tenantId, request);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.employeeId()).isEqualTo(employeeId);
            assertThat(result.benefitPlanId()).isEqualTo(planId);
            verify(enrollmentRepository).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("should throw when employee not found")
        void shouldThrowWhenEmployeeNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.createEnrollment(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Employee");
        }

        @Test
        @DisplayName("should throw when plan not found")
        void shouldThrowWhenPlanNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.createEnrollment(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("BenefitPlan");
        }

        @Test
        @DisplayName("should throw when employee belongs to different tenant")
        void shouldThrowWhenEmployeeBelongsToDifferentTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId(UUID.randomUUID());
            employee.setTenant(otherTenant);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

            assertThatThrownBy(() -> enrollmentService.createEnrollment(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Employee does not belong to tenant");
        }

        @Test
        @DisplayName("should throw when plan belongs to different tenant")
        void shouldThrowWhenPlanBelongsToDifferentTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId(UUID.randomUUID());
            plan.setTenant(otherTenant);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

            assertThatThrownBy(() -> enrollmentService.createEnrollment(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BenefitPlan does not belong to tenant");
        }

        @Test
        @DisplayName("should throw when employee already enrolled in plan")
        void shouldThrowWhenAlreadyEnrolled() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(enrollmentRepository.existsByEmployeeIdAndBenefitPlanIdAndStatusNot(
                    employeeId, planId, EnrollmentStatus.CANCELLED)).thenReturn(true);

            assertThatThrownBy(() -> enrollmentService.createEnrollment(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Employee is already enrolled in this plan");
        }
    }

    @Nested
    @DisplayName("getEnrollments")
    class GetEnrollmentsTests {

        @Test
        @DisplayName("should return enrollments for tenant")
        void shouldReturnEnrollments() {
            when(enrollmentRepository.findByTenantId(tenantId)).thenReturn(List.of(enrollment));
            when(enrollmentMapper.toResponseList(List.of(enrollment))).thenReturn(List.of(enrollmentResponse));

            List<EnrollmentResponse> result = enrollmentService.getEnrollments(tenantId);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getEnrollmentById")
    class GetEnrollmentByIdTests {

        @Test
        @DisplayName("should return enrollment when found")
        void shouldReturnEnrollment() {
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
            when(enrollmentMapper.toResponse(enrollment)).thenReturn(enrollmentResponse);

            EnrollmentResponse result = enrollmentService.getEnrollmentById(tenantId, enrollmentId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(enrollmentId);
        }

        @Test
        @DisplayName("should throw when enrollment belongs to different tenant")
        void shouldThrowWhenDifferentTenant() {
            UUID otherTenantId = UUID.randomUUID();
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.getEnrollmentById(otherTenantId, enrollmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("cancelEnrollment")
    class CancelEnrollmentTests {

        @Test
        @DisplayName("should cancel active enrollment")
        void shouldCancelEnrollment() {
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
            when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

            enrollmentService.cancelEnrollment(tenantId, enrollmentId);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getTerminationDate()).isEqualTo(LocalDate.now());
            verify(enrollmentRepository).save(enrollment);
        }

        @Test
        @DisplayName("should cancel pending enrollment")
        void shouldCancelPendingEnrollment() {
            enrollment.setStatus(EnrollmentStatus.PENDING);
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
            when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

            enrollmentService.cancelEnrollment(tenantId, enrollmentId);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw when enrollment already cancelled")
        void shouldThrowWhenAlreadyCancelled() {
            enrollment.setStatus(EnrollmentStatus.CANCELLED);
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancelEnrollment(tenantId, enrollmentId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Enrollment is already cancelled");

            verify(enrollmentRepository, never()).save(any());
        }
    }
}

