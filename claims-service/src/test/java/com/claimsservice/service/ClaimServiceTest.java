package com.claimsservice.service;

import com.claimsservice.client.BenefitsServiceClient;
import com.claimsservice.dto.ClaimResponse;
import com.claimsservice.dto.CreateClaimRequest;
import com.claimsservice.dto.EnrollmentValidationResponse;
import com.claimsservice.dto.UpdateClaimRequest;
import com.claimsservice.entity.Claim;
import com.claimsservice.entity.ClaimStatus;
import com.claimsservice.exception.ResourceNotFoundException;
import com.claimsservice.repository.ClaimRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private BenefitsServiceClient benefitsServiceClient;

    private ClaimService claimService;

    private UUID tenantId;
    private UUID employeeId;
    private UUID enrollmentId;
    private UUID claimId;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(claimRepository, benefitsServiceClient, new SimpleMeterRegistry());
        tenantId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        claimId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("createClaim")
    class CreateClaim {

        @Test
        @DisplayName("should create claim when enrollment is valid")
        void shouldCreateClaimWhenEnrollmentValid() {
            // Given
            CreateClaimRequest request = new CreateClaimRequest(
                    employeeId, enrollmentId, LocalDate.now(),
                    "Dr. Smith", "J45.0", new BigDecimal("1500.00"));

            EnrollmentValidationResponse validation = new EnrollmentValidationResponse(
                    enrollmentId, tenantId, employeeId, UUID.randomUUID(), "ACTIVE", true);

            when(benefitsServiceClient.validateEnrollment(enrollmentId, tenantId))
                    .thenReturn(validation);

            Claim savedClaim = buildClaim(ClaimStatus.DRAFT);
            when(claimRepository.save(any(Claim.class))).thenReturn(savedClaim);

            // When
            ClaimResponse response = claimService.createClaim(tenantId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("DRAFT");
            assertThat(response.tenantId()).isEqualTo(tenantId);
            verify(benefitsServiceClient).validateEnrollment(enrollmentId, tenantId);
            verify(claimRepository).save(any(Claim.class));
        }

        @Test
        @DisplayName("should throw when enrollment not found")
        void shouldThrowWhenEnrollmentNotFound() {
            CreateClaimRequest request = new CreateClaimRequest(
                    employeeId, enrollmentId, LocalDate.now(),
                    "Dr. Smith", "J45.0", new BigDecimal("1500.00"));

            when(benefitsServiceClient.validateEnrollment(enrollmentId, tenantId))
                    .thenReturn(null);

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Enrollment");
        }

        @Test
        @DisplayName("should throw when enrollment is not valid")
        void shouldThrowWhenEnrollmentNotValid() {
            CreateClaimRequest request = new CreateClaimRequest(
                    employeeId, enrollmentId, LocalDate.now(),
                    "Dr. Smith", "J45.0", new BigDecimal("1500.00"));

            EnrollmentValidationResponse validation = new EnrollmentValidationResponse(
                    enrollmentId, tenantId, employeeId, UUID.randomUUID(), "CANCELLED", false);

            when(benefitsServiceClient.validateEnrollment(enrollmentId, tenantId))
                    .thenReturn(validation);

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not valid");
        }

        @Test
        @DisplayName("should throw when employee does not match enrollment")
        void shouldThrowWhenEmployeeMismatch() {
            UUID differentEmployeeId = UUID.randomUUID();
            CreateClaimRequest request = new CreateClaimRequest(
                    differentEmployeeId, enrollmentId, LocalDate.now(),
                    "Dr. Smith", "J45.0", new BigDecimal("1500.00"));

            EnrollmentValidationResponse validation = new EnrollmentValidationResponse(
                    enrollmentId, tenantId, employeeId, UUID.randomUUID(), "ACTIVE", true);

            when(benefitsServiceClient.validateEnrollment(enrollmentId, tenantId))
                    .thenReturn(validation);

            assertThatThrownBy(() -> claimService.createClaim(tenantId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Employee does not match");
        }
    }

    @Nested
    @DisplayName("getClaims")
    class GetClaims {

        @Test
        @DisplayName("should return paginated claims for tenant")
        void shouldReturnPaginatedClaims() {
            Claim claim = buildClaim(ClaimStatus.DRAFT);
            Page<Claim> page = new PageImpl<>(List.of(claim));
            when(claimRepository.findByTenantId(tenantId, PageRequest.of(0, 10)))
                    .thenReturn(page);

            Page<ClaimResponse> result = claimService.getClaims(tenantId, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).tenantId()).isEqualTo(tenantId);
        }
    }

    @Nested
    @DisplayName("getClaimById")
    class GetClaimById {

        @Test
        @DisplayName("should return claim when found")
        void shouldReturnClaim() {
            Claim claim = buildClaim(ClaimStatus.SUBMITTED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            ClaimResponse response = claimService.getClaimById(tenantId, claimId);

            assertThat(response.id()).isEqualTo(claimId);
            assertThat(response.status()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("should throw when claim not found")
        void shouldThrowWhenNotFound() {
            when(claimRepository.findById(claimId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> claimService.getClaimById(tenantId, claimId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when claim belongs to different tenant")
        void shouldThrowWhenDifferentTenant() {
            Claim claim = buildClaim(ClaimStatus.DRAFT);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            UUID otherTenantId = UUID.randomUUID();
            assertThatThrownBy(() -> claimService.getClaimById(otherTenantId, claimId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateClaim")
    class UpdateClaim {

        @Test
        @DisplayName("should update claim in DRAFT status")
        void shouldUpdateDraftClaim() {
            Claim claim = buildClaim(ClaimStatus.DRAFT);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimRepository.save(any(Claim.class))).thenReturn(claim);

            UpdateClaimRequest request = new UpdateClaimRequest(
                    LocalDate.now().plusDays(1), "New Provider", "K21.0", new BigDecimal("2000.00"));

            ClaimResponse response = claimService.updateClaim(tenantId, claimId, request);

            assertThat(response).isNotNull();
            verify(claimRepository).save(any(Claim.class));
        }

        @Test
        @DisplayName("should throw when updating non-DRAFT claim")
        void shouldThrowWhenNotDraft() {
            Claim claim = buildClaim(ClaimStatus.SUBMITTED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            UpdateClaimRequest request = new UpdateClaimRequest(
                    LocalDate.now(), null, null, null);

            assertThatThrownBy(() -> claimService.updateClaim(tenantId, claimId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    @Nested
    @DisplayName("deleteClaim")
    class DeleteClaim {

        @Test
        @DisplayName("should delete DRAFT claim")
        void shouldDeleteDraftClaim() {
            Claim claim = buildClaim(ClaimStatus.DRAFT);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            claimService.deleteClaim(tenantId, claimId);

            verify(claimRepository).delete(claim);
        }

        @Test
        @DisplayName("should throw when deleting non-DRAFT claim")
        void shouldThrowWhenDeletingNonDraft() {
            Claim claim = buildClaim(ClaimStatus.APPROVED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.deleteClaim(tenantId, claimId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    @Nested
    @DisplayName("submitClaim")
    class SubmitClaim {

        @Test
        @DisplayName("should submit DRAFT claim")
        void shouldSubmitDraftClaim() {
            Claim claim = buildClaim(ClaimStatus.DRAFT);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
            when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> {
                Claim saved = invocation.getArgument(0);
                return saved;
            });

            ClaimResponse response = claimService.submitClaim(tenantId, claimId);

            assertThat(response.status()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("should throw when submitting non-DRAFT claim")
        void shouldThrowWhenSubmittingNonDraft() {
            Claim claim = buildClaim(ClaimStatus.SUBMITTED);
            when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> claimService.submitClaim(tenantId, claimId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    // --- Helper ---

    private Claim buildClaim(ClaimStatus status) {
        Claim claim = new Claim();
        claim.setId(claimId);
        claim.setTenantId(tenantId);
        claim.setEmployeeId(employeeId);
        claim.setEnrollmentId(enrollmentId);
        claim.setClaimNumber("CLM-2026-000001");
        claim.setStatus(status);
        claim.setServiceDate(LocalDate.now());
        claim.setProviderName("Dr. Smith");
        claim.setDiagnosisCode("J45.0");
        claim.setClaimedAmount(new BigDecimal("1500.00"));
        return claim;
    }
}

