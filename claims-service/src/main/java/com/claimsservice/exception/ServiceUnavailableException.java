package com.claimsservice.exception;

/**
 * Thrown when a downstream service is unavailable (circuit breaker open or connection failed).
 * Maps to HTTP 503 Service Unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

