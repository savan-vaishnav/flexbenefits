package com.claimsservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handles cache eviction for enrollment validation cache.
 * Called by EnrollmentEventConsumer when enrollment status changes via Kafka.
 *
 * Why a separate class?
 *   - @CacheEvict requires Spring AOP proxy to intercept the method call
 *   - Self-invocation within the same class bypasses the proxy
 *   - Kafka listener calling a method on THIS object won't trigger @CacheEvict
 *   - Solution: extract eviction to a separate bean → proxy intercepts correctly
 */
@Component
@Slf4j
public class CacheEvictionService {

    @CacheEvict(value = "enrollment-validation", key = "#enrollmentId + '-' + #tenantId")
    public void evictEnrollmentValidationCache(UUID enrollmentId, UUID tenantId) {
        log.info("Evicted enrollment-validation cache for enrollment: {}, tenant: {}", enrollmentId, tenantId);
    }
}

