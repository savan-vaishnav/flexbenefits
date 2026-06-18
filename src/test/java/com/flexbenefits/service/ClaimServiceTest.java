package com.flexbenefits.service;

import com.flexbenefits.dto.ClaimResponse;
import com.flexbenefits.dto.CreateClaimRequest;
import com.flexbenefits.dto.UpdateClaimRequest;
import com.flexbenefits.entity.*;
import com.flexbenefits.entity.enums.ClaimStatus;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.ClaimMapper;
import com.flexbenefits.repository.ClaimRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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
@DisplayName("ClaimService Unit Tests")
class ClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ClaimMapper claimMapper;

    @InjectMocks
    private ClaimService claimService;

    private UUID tenantId;
    private UUID employeeId;
    private UUID enrollmentId;
    private UUID claimId;
    private Tenant tenant;
    private Employee employee;
    private Enrollment enrollment;
    private Claim claim;
    private ClaimResponse claimResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        claimId = UUID.randomUUID();

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Corp");
        tenant.setCode("ACME");

        employee = new Employee();
        employee.setId(employeeId);
        employee.setTenant(tenant);
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john@acme.com");

        enrollment = new Enrollment();
        enrollment.setId(enrollmentId);
        enrollment.setTenant(tenant);
        enrollment.setEmployee(employee);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        claim = new Claim();
        claim.setId(claimId);
        claim.setTenant(tenant);
        claim.setEmployee(employee);
        claim.setEnrollment(enrollment);
        claim.setClaimNumber("CLM-2026-000001");
        claim.setStatus(ClaimStatus.DRAFT);
        claim.setServiceDate(LocalDate.of(2026, 6, 10));
        claim.setProviderName("Dr. Smith");
        claim.setDiagnosisCode("J06.9");
        claim.setClaimedAmount(new BigDecimal("5000.00"));
        claim.setCreatedAt(LocalDateTime.now());

        claimResponse = new ClaimResponse(
                claimId, tenantId, employeeId,
                "CLM-2026-000001", "DRAFT",
                LocalDate.of(2026, 6, 10), "Dr. Smith",
                "J06.9", new BigDecimal("5000.00"),
                null, null, null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("createClaim")
    class CreateClaimTests {

        private CreateClaimRequest request;

        @BeforeEach
        void setUp() {
            request = new CreateClaimRequest(
                    employeeId, enrollmentId,
                    LocalDate.of(2026, 6, 10),
                    "Dr. Smith", "J06.9",
                    new BigDecimal("5000.00")
            );
        }

        @Test
        @DisplayName("should create claim successfully with valid data")
        void shouldCreateClaimSuccessfully() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
            when(claimRepository.save(any(Claim.class))).thenReturn(claim);
            when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

            ClaimResponse result = claimService.createClaim(tenantId, request);

            assertThat(result).isNotNull();
            assertThat(result.claimNumber()).isEqualTo("CLM-2026-000001");
            assertThat(result.status()).isEqualTo("DRAFT");
            assertThat(result.claimedAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));

            verify(claimRepository).save(any(Claim.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Tenant");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when employee not found")
        void shouldThrowWhenEmployeeNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Employee");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when enrollment not found")
        void shouldThrowWhenEnrollmentNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Enrollment");
        }

        @Test
        @DisplayName("should throw IllegalStateException when employee belongs to different tenant")
        void shouldThrowWhenEmployeeBelongsToDifferentTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId(UUID.randomUUID());
            employee.setTenant(otherTenant);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Employee does not belong to tenant");
        }

        @Test
        @DisplayName("should throw IllegalStateException when enrollment belongs to different tenant")
        void shouldThrowWhenEnrollmentBelongsToDifferentTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId(UUID.randomUUID());
            enrollment.setTenant(otherTenant);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Enrollment does not belong to tenant");
        }
    }

    @Nested
    @DisplayName("getClaims")
    class GetClaimsTests {

        @Test
        @DisplayName("should return paginated claims for tenant")
        void shouldReturnPaginatedClaims() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Claim> claimPage = new PageImpl<>(List.of(claim), pageable, 1);

            when(claimRepository.findByTenantId(tenantId, pageable)).thenReturn(claimPage);
            when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

            Page<ClaimResponse> result = claimService.getClaims(tenantId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().claimNumber()).isEqualTo("CLM-2026-000001");
        }
    }

    @Nested
    @DisplayName("getClaimById")
    class GetClaimByIdTests {

        @Test
        @DisplayName("should return claim when found and belongs to tenant")
        void shouldReturnClaimWhenFound() {
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

            ClaimResponse result = claimService.getClaimById(tenantId, claimId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(claimId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when claim not found")
        void shouldThrowWhenClaimNotFound() {
            when(claimRepository.findById(claimId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> claimService.getClaimById(tenantId, claimId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when claim belongs to different tenant")
        void shouldThrowWhenClaimBelongsToDifferentTenant() {
            UUID otherTenantId = UUID.randomUUID();
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.getClaimById(otherTenantId, claimId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateClaim")
    class UpdateClaimTests {

        @Test
        @DisplayName("should update claim fields when in DRAFT status")
        void shouldUpdateDraftClaim() {
            UpdateClaimRequest updateRequest = new UpdateClaimRequest(
                    LocalDate.of(2026, 6, 15), "Dr. Jones", null, new BigDecimal("6000.00")
            );

            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimRepository.save(any(Claim.class))).thenReturn(claim);
            when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);

            ClaimResponse result = claimService.updateClaim(tenantId, claimId, updateRequest);

            assertThat(result).isNotNull();
            verify(claimRepository).save(any(Claim.class));
        }

        @Test
        @DisplayName("should only update non-null fields (partial update)")
        void shouldOnlyUpdateNonNullFields() {
            UpdateClaimRequest updateRequest = new UpdateClaimRequest(
                    null, "Dr. Jones", null, null
            );

            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimRepository.save(any(Claim.class))).thenReturn(claim);
            when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);

            claimService.updateClaim(tenantId, claimId, updateRequest);

            // serviceDate should remain unchanged
            assertThat(claim.getServiceDate()).isEqualTo(LocalDate.of(2026, 6, 10));
            // providerName should be updated
            assertThat(claim.getProviderName()).isEqualTo("Dr. Jones");
        }

        @Test
        @DisplayName("should throw IllegalStateException when updating non-DRAFT claim")
        void shouldThrowWhenUpdatingNonDraftClaim() {
            claim.setStatus(ClaimStatus.SUBMITTED);
            UpdateClaimRequest updateRequest = new UpdateClaimRequest(
                    null, "Dr. Jones", null, null
            );

            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.updateClaim(tenantId, claimId, updateRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only update claims in DRAFT status");
        }
    }

    @Nested
    @DisplayName("deleteClaim")
    class DeleteClaimTests {

        @Test
        @DisplayName("should delete DRAFT claim")
        void shouldDeleteDraftClaim() {
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            claimService.deleteClaim(tenantId, claimId);

            verify(claimRepository).delete(claim);
        }

        @Test
        @DisplayName("should throw IllegalStateException when deleting non-DRAFT claim")
        void shouldThrowWhenDeletingNonDraftClaim() {
            claim.setStatus(ClaimStatus.APPROVED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.deleteClaim(tenantId, claimId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only delete claims in DRAFT status");

            verify(claimRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("submitClaim")
    class SubmitClaimTests {

        @Test
        @DisplayName("should transition DRAFT claim to SUBMITTED")
        void shouldSubmitDraftClaim() {
            ClaimResponse submittedResponse = new ClaimResponse(
                    claimId, tenantId, employeeId,
                    "CLM-2026-000001", "SUBMITTED",
                    LocalDate.of(2026, 6, 10), "Dr. Smith",
                    "J06.9", new BigDecimal("5000.00"),
                    null, null, LocalDateTime.now(), LocalDateTime.now()
            );

            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimRepository.save(any(Claim.class))).thenReturn(claim);
            when(claimMapper.toResponse(any(Claim.class))).thenReturn(submittedResponse);

            ClaimResponse result = claimService.submitClaim(tenantId, claimId);

            assertThat(result.status()).isEqualTo("SUBMITTED");
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(claim.getSubmittedAt()).isNotNull();
            verify(claimRepository).save(claim);
        }

        @Test
        @DisplayName("should throw IllegalStateException when submitting non-DRAFT claim")
        void shouldThrowWhenSubmittingNonDraftClaim() {
            claim.setStatus(ClaimStatus.SUBMITTED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.submitClaim(tenantId, claimId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only submit claims in DRAFT status");
        }
    }
}

