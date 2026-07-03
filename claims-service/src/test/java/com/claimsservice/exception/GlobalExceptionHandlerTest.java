package com.claimsservice.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that GlobalExceptionHandler returns correct HTTP status codes
 * for Resilience4j exceptions and business exceptions.
 */
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("should return 404 for ResourceNotFoundException")
    void shouldReturn404ForNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Claim", java.util.UUID.randomUUID());

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("error")).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("should return 503 for ServiceUnavailableException")
    void shouldReturn503ForServiceUnavailable() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Benefits service is down");

        ResponseEntity<Map<String, Object>> response = handler.handleServiceUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("message")).isEqualTo("Benefits service is down");
        assertThat(response.getBody().get("error")).isEqualTo("Service Unavailable");
    }

    @Test
    @DisplayName("should return 503 for CallNotPermittedException (circuit breaker OPEN)")
    void shouldReturn503ForCircuitBreakerOpen() {
        // Create a real CallNotPermittedException
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("testCB");
        cb.transitionToOpenState();

        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<Map<String, Object>> response = handler.handleCircuitBreakerOpen(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("error")).isEqualTo("Service Unavailable");
        assertThat(response.getBody().get("circuitBreaker")).isEqualTo("testCB");
    }

    @Test
    @DisplayName("should return 429 for RequestNotPermitted (rate limiter)")
    void shouldReturn429ForRateLimitExceeded() {
        // Create a real RequestNotPermitted by exhausting a rate limiter
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(java.time.Duration.ofMinutes(1))
                .timeoutDuration(java.time.Duration.ZERO)
                .build();
        RateLimiter rl = RateLimiterRegistry.of(config).rateLimiter("testRL");
        rl.acquirePermission(); // exhaust the single permit

        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(rl);

        ResponseEntity<Map<String, Object>> response = handler.handleRateLimiter(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().get("error")).isEqualTo("Too Many Requests");
        assertThat(response.getBody().get("message")).isEqualTo("Rate limit exceeded. Please slow down.");
    }

    @Test
    @DisplayName("should return 409 for IllegalStateException")
    void shouldReturn409ForIllegalState() {
        IllegalStateException ex = new IllegalStateException("Can only submit claims in DRAFT status");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).isEqualTo("Can only submit claims in DRAFT status");
    }

    @Test
    @DisplayName("should return 500 for generic RuntimeException")
    void shouldReturn500ForRuntimeException() {
        RuntimeException ex = new RuntimeException("Something unexpected");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("Something unexpected");
    }

    @Test
    @DisplayName("should handle null message in RuntimeException")
    void shouldHandleNullMessage() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
    }
}


