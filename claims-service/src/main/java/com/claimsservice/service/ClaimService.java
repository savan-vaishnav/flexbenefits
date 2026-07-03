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
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
@Slf4j
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final BenefitsServiceClient benefitsServiceClient;
    private final MeterRegistry meterRegistry;
    private final Counter claimsCreatedCounter;
    private final Counter claimsSubmittedCounter;
    private final Timer claimProcessingTimer;

    private final AtomicLong claimSequence = new AtomicLong(1);

    public ClaimService(ClaimRepository claimRepository,
                        BenefitsServiceClient benefitsServiceClient,
                        MeterRegistry meterRegistry) {
        this.claimRepository = claimRepository;
        this.benefitsServiceClient = benefitsServiceClient;
        this.meterRegistry = meterRegistry;

        // Register custom metrics
        this.claimsCreatedCounter = Counter.builder("claims.created")
                .description("Total number of claims created")
                .register(meterRegistry);
        this.claimsSubmittedCounter = Counter.builder("claims.submitted")
                .description("Total number of claims submitted")
                .register(meterRegistry);
        this.claimProcessingTimer = Timer.builder("claims.processing.time")
                .description("Time taken to create a claim (including validation)")
                .register(meterRegistry);
    }

    public ClaimResponse createClaim(UUID tenantId, CreateClaimRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Creating claim for tenant: {}, employee: {}, enrollment: {}",
                tenantId, request.employeeId(), request.enrollmentId());

        // Validate enrollment via benefits-service (inter-service REST call)
        EnrollmentValidationResponse validation = benefitsServiceClient
                .validateEnrollment(request.enrollmentId(), tenantId);

        if (validation == null) {
            throw new ResourceNotFoundException("Enrollment", request.enrollmentId());
        }

        if (!validation.valid()) {
            throw new IllegalStateException(
                    "Enrollment is not valid for claims. Status: " + validation.status());
        }

        // Verify employee matches enrollment
        if (!validation.employeeId().equals(request.employeeId())) {
            throw new IllegalStateException("Employee does not match enrollment");
        }

        Claim claim = new Claim();
        claim.setTenantId(tenantId);
        claim.setEmployeeId(request.employeeId());
        claim.setEnrollmentId(request.enrollmentId());
        claim.setClaimNumber(generateClaimNumber());
        claim.setStatus(ClaimStatus.DRAFT);
        claim.setServiceDate(request.serviceDate());
        claim.setProviderName(request.providerName());
        claim.setDiagnosisCode(request.diagnosisCode());
        claim.setClaimedAmount(request.claimedAmount());

        Claim saved = claimRepository.save(claim);
        claimsCreatedCounter.increment();
        sample.stop(claimProcessingTimer);
        log.info("Claim created: {} with number: {}", saved.getId(), saved.getClaimNumber());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getClaims(UUID tenantId, Pageable pageable) {
        return claimRepository.findByTenantId(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimById(UUID tenantId, UUID claimId) {
        Claim claim = findClaimByTenantAndId(tenantId, claimId);
        return toResponse(claim);
    }

    public ClaimResponse updateClaim(UUID tenantId, UUID claimId, UpdateClaimRequest request) {
        Claim claim = findClaimByTenantAndId(tenantId, claimId);

        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new IllegalStateException(
                    "Can only update claims in DRAFT status. Current status: " + claim.getStatus());
        }

        if (request.serviceDate() != null) claim.setServiceDate(request.serviceDate());
        if (request.providerName() != null) claim.setProviderName(request.providerName());
        if (request.diagnosisCode() != null) claim.setDiagnosisCode(request.diagnosisCode());
        if (request.claimedAmount() != null) claim.setClaimedAmount(request.claimedAmount());

        Claim saved = claimRepository.save(claim);
        return toResponse(saved);
    }

    public void deleteClaim(UUID tenantId, UUID claimId) {
        Claim claim = findClaimByTenantAndId(tenantId, claimId);

        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new IllegalStateException(
                    "Can only delete claims in DRAFT status. Current status: " + claim.getStatus());
        }

        log.info("Deleting claim: {} for tenant: {}", claimId, tenantId);
        claimRepository.delete(claim);
    }

    @RateLimiter(name = "claimSubmission")
    public ClaimResponse submitClaim(UUID tenantId, UUID claimId) {
        Claim claim = findClaimByTenantAndId(tenantId, claimId);

        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new IllegalStateException(
                    "Can only submit claims in DRAFT status. Current status: " + claim.getStatus());
        }

        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(LocalDateTime.now());

        Claim saved = claimRepository.save(claim);
        claimsSubmittedCounter.increment();
        log.info("Claim submitted: {} with number: {}", saved.getId(), saved.getClaimNumber());
        return toResponse(saved);
    }

    // --- Private helpers ---

    private Claim findClaimByTenantAndId(UUID tenantId, UUID claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));

        if (!claim.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Claim", claimId);
        }
        return claim;
    }

    private String generateClaimNumber() {
        return "CLM-" + Year.now().getValue() + "-" + String.format("%06d", claimSequence.getAndIncrement());
    }

    private ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getTenantId(),
                claim.getEmployeeId(),
                claim.getEnrollmentId(),
                claim.getClaimNumber(),
                claim.getStatus().name(),
                claim.getServiceDate(),
                claim.getProviderName(),
                claim.getDiagnosisCode(),
                claim.getClaimedAmount(),
                claim.getApprovedAmount(),
                claim.getRejectionReason(),
                claim.getSubmittedAt(),
                claim.getCreatedAt()
        );
    }
}

