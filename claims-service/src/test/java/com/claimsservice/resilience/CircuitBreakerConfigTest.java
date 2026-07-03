package com.claimsservice.resilience;

import com.claimsservice.client.BenefitsServiceClient;
import com.claimsservice.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests Resilience4j circuit breaker behavior programmatically.
 * These tests verify the CB state transitions without needing Spring context.
 *
 * Note: Annotation-based tests (@CircuitBreaker on methods) require Spring AOP proxy,
 * which means full Spring Boot integration tests. These programmatic tests verify
 * the configuration and state machine logic independently.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Circuit Breaker Configuration Tests")
class CircuitBreakerConfigTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Create a CB with the same config as application.properties
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("benefitsService");
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("should start in CLOSED state")
        void shouldStartClosed() {
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should stay CLOSED when failure rate is below threshold")
        void shouldStayClosedBelowThreshold() {
            // Record 5 calls: 2 failures + 3 successes = 40% failure rate < 50%
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isLessThan(50f);
        }

        @Test
        @DisplayName("should transition to OPEN when failure rate exceeds threshold")
        void shouldOpenWhenFailureRateExceedsThreshold() {
            // Record 5 calls: 3 failures + 2 successes = 60% failure rate > 50%
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isGreaterThanOrEqualTo(50f);
        }

        @Test
        @DisplayName("should not open until minimum number of calls reached")
        void shouldNotOpenBeforeMinimumCalls() {
            // Record 4 calls: all failures — but minimum is 5, so CB stays CLOSED
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should transition to HALF_OPEN after wait duration")
        void shouldTransitionToHalfOpen() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Force transition to HALF_OPEN (normally waits 30s, we force it)
            circuitBreaker.transitionToHalfOpenState();

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("should close from HALF_OPEN after successful test calls")
        void shouldCloseFromHalfOpenOnSuccess() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            }

            // Force to HALF_OPEN
            circuitBreaker.transitionToHalfOpenState();

            // 3 successful test calls (permitted-number-of-calls-in-half-open-state=3)
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should re-open from HALF_OPEN on failure")
        void shouldReopenFromHalfOpenOnFailure() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            }

            // Force to HALF_OPEN
            circuitBreaker.transitionToHalfOpenState();

            // 2 failures + 1 success in test calls = 66% > 50% → back to OPEN
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should track successful and failed calls")
        void shouldTrackMetrics() {
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException());

            assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
            assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        }
    }
}

