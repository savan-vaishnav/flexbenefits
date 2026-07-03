package com.claimsservice.client;

import com.claimsservice.dto.EnrollmentValidationResponse;
import com.claimsservice.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * REST client for inter-service communication with benefits-service.
 * Protected by Resilience4j circuit breaker and retry.
 *
 * Resilience pattern:
 *   Request → Retry (max 3, exponential backoff) → Circuit Breaker → HTTP call
 *   If all retries fail → CB counts as failure → after threshold → CB opens → fallback immediately
 */
@Component
@Slf4j
public class BenefitsServiceClient {

    private final RestClient restClient;

    public BenefitsServiceClient(
            @Value("${benefits-service.url:http://localhost:8082}") String benefitsServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(benefitsServiceUrl)
                .build();
    }

    /**
     * Validates enrollment with benefits-service.
     * Protected by circuit breaker (opens after 50% failure rate in sliding window of 10 calls)
     * and retry (max 3 attempts with exponential backoff).
     *
     * @param enrollmentId the enrollment UUID to validate
     * @param tenantId     the tenant UUID for scoping
     * @return validation response or null if enrollment not found
     */
    @Cacheable(value = "enrollment-validation", key = "#enrollmentId + '-' + #tenantId")
    @CircuitBreaker(name = "benefitsService", fallbackMethod = "validateEnrollmentFallback")
    @Retry(name = "benefitsService")
    public EnrollmentValidationResponse validateEnrollment(UUID enrollmentId, UUID tenantId) {
        log.info("Cache MISS — calling benefits-service to validate enrollment: {} for tenant: {}", enrollmentId, tenantId);

        return restClient.get()
                .uri("/api/internal/v1/enrollments/{enrollmentId}/validate?tenantId={tenantId}",
                        enrollmentId, tenantId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("Enrollment not found: {} - status: {}", enrollmentId, response.getStatusCode());
                })
                .body(EnrollmentValidationResponse.class);
    }

    /**
     * Fallback method invoked when circuit breaker is OPEN or all retries exhausted.
     * Throws ServiceUnavailableException which maps to HTTP 503.
     */
    private EnrollmentValidationResponse validateEnrollmentFallback(UUID enrollmentId, UUID tenantId, Throwable t) {
        log.warn("Circuit breaker fallback for enrollment {}: {} - {}", enrollmentId, t.getClass().getSimpleName(), t.getMessage());
        throw new ServiceUnavailableException(
                "Benefits service is currently unavailable. Cannot validate enrollment. Please try again later.");
    }
}
