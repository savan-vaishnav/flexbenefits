package com.claimsservice.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests Retry configuration programmatically.
 * Verifies retry behavior (max attempts, retryable exceptions).
 */
@DisplayName("Retry Configuration Tests")
class RetryConfigTest {

    private Retry retry;

    @BeforeEach
    void setUp() {
        // Create a retry matching application.properties config (without wait for speed)
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10)) // short wait for test speed
                .retryExceptions(ResourceAccessException.class, ConnectException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        retry = registry.retry("benefitsService");
    }

    @Test
    @DisplayName("should succeed on first attempt without retry")
    void shouldSucceedFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            return "success";
        });

        String result = supplier.get();

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("should retry and succeed on second attempt")
    void shouldRetryAndSucceedOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            if (attempts.get() < 2) {
                throw new ResourceAccessException("Connection refused");
            }
            return "success after retry";
        });

        String result = supplier.get();

        assertThat(result).isEqualTo("success after retry");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("should retry max 3 times then throw")
    void shouldExhaustRetriesAndThrow() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("Connection refused");
        });

        assertThatThrownBy(supplier::get)
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Connection refused");

        assertThat(attempts.get()).isEqualTo(3); // initial + 2 retries
    }

    @Test
    @DisplayName("should NOT retry non-retryable exceptions")
    void shouldNotRetryNonRetryableExceptions() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Business logic error — not retryable");
        });

        assertThatThrownBy(supplier::get)
                .isInstanceOf(IllegalStateException.class);

        assertThat(attempts.get()).isEqualTo(1); // no retry for non-configured exception
    }

    @Test
    @DisplayName("should retry on ConnectException")
    void shouldRetryOnConnectException() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            if (attempts.get() < 3) {
                throw new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
            }
            return "recovered";
        });

        String result = supplier.get();

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("should track retry metrics")
    void shouldTrackMetrics() {
        AtomicInteger attempts = new AtomicInteger(0);

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            if (attempts.get() < 2) {
                throw new ResourceAccessException("fail once");
            }
            return "ok";
        });

        supplier.get();

        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    }
}

