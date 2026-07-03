package com.claimsservice.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests Rate Limiter configuration programmatically.
 * Verifies that the rate limiter correctly permits/denies requests
 * based on the configured limits.
 */
@DisplayName("Rate Limiter Configuration Tests")
class RateLimiterConfigTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Create a rate limiter matching application.properties config
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)                      // 10 calls per period
                .limitRefreshPeriod(Duration.ofSeconds(1)) // refresh every 1s
                .timeoutDuration(Duration.ZERO)           // don't wait — reject immediately
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        rateLimiter = registry.rateLimiter("claimSubmission");
    }

    @Test
    @DisplayName("should permit requests within limit")
    void shouldPermitWithinLimit() {
        // 10 requests should all be permitted
        for (int i = 0; i < 10; i++) {
            boolean permitted = rateLimiter.acquirePermission();
            assertThat(permitted).isTrue();
        }
    }

    @Test
    @DisplayName("should reject requests exceeding limit")
    void shouldRejectExceedingLimit() {
        // Exhaust the 10 permits
        for (int i = 0; i < 10; i++) {
            rateLimiter.acquirePermission();
        }

        // 11th request should be rejected
        boolean permitted = rateLimiter.acquirePermission();
        assertThat(permitted).isFalse();
    }

    @Test
    @DisplayName("should have correct available permissions initially")
    void shouldHaveCorrectInitialPermissions() {
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(10);
    }

    @Test
    @DisplayName("should decrease available permissions on acquire")
    void shouldDecreasePermissionsOnAcquire() {
        rateLimiter.acquirePermission();
        rateLimiter.acquirePermission();
        rateLimiter.acquirePermission();

        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(7);
    }

    @Test
    @DisplayName("should track number of waiting threads as zero with timeout=0")
    void shouldHaveNoWaitingThreads() {
        // With timeout=0, no threads wait — they get rejected immediately
        for (int i = 0; i < 10; i++) {
            rateLimiter.acquirePermission();
        }

        assertThat(rateLimiter.getMetrics().getNumberOfWaitingThreads()).isEqualTo(0);
    }
}

