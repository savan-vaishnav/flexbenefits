package com.claimsservice.event;

import com.claimsservice.config.CacheEvictionService;
import com.claimsservice.entity.Claim;
import com.claimsservice.entity.ClaimStatus;
import com.claimsservice.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consumes enrollment events from Kafka.
 * When an enrollment is CANCELLED, auto-rejects all DRAFT claims linked to that enrollment.
 * Also evicts the enrollment-validation cache so subsequent calls get fresh data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrollmentEventConsumer {

    private final ClaimRepository claimRepository;
    private final CacheEvictionService cacheEvictionService;

    @KafkaListener(topics = "enrollment-events", groupId = "${spring.kafka.consumer.group-id:claims-service}")
    @Transactional
    public void handleEnrollmentEvent(EnrollmentEvent event) {
        log.info("Received enrollment event: enrollmentId={}, status={}, tenantId={}",
                event.enrollmentId(), event.status(), event.tenantId());

        // Evict cached enrollment validation — status has changed
        cacheEvictionService.evictEnrollmentValidationCache(event.enrollmentId(), event.tenantId());

        if ("CANCELLED".equals(event.status())) {
            handleEnrollmentCancelled(event);
        }
    }

    private void handleEnrollmentCancelled(EnrollmentEvent event) {
        // Find all DRAFT claims for this enrollment
        List<Claim> draftClaims = claimRepository
                .findByEnrollmentIdAndStatus(event.enrollmentId(), ClaimStatus.DRAFT);

        if (draftClaims.isEmpty()) {
            log.info("No DRAFT claims found for cancelled enrollment: {}", event.enrollmentId());
            return;
        }

        // Auto-reject all draft claims
        draftClaims.forEach(claim -> {
            claim.setStatus(ClaimStatus.REJECTED);
            claim.setRejectionReason("Enrollment cancelled — auto-rejected by system");
            claim.setAdjudicatedAt(LocalDateTime.now());
        });

        claimRepository.saveAll(draftClaims);
        log.info("Auto-rejected {} DRAFT claim(s) for cancelled enrollment: {}",
                draftClaims.size(), event.enrollmentId());
    }
}

