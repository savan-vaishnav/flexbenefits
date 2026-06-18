package com.flexbenefits.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis-based idempotency filter.
 * Clients send an "Idempotency-Key" header with POST/PUT/PATCH requests.
 * If the key was already seen (within 24h), return 409 Conflict.
 * This prevents duplicate claim submissions from network retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Only apply to mutating methods
        String method = request.getMethod();
        if (!HttpMethod.POST.name().equals(method)
                && !HttpMethod.PUT.name().equals(method)
                && !HttpMethod.PATCH.name().equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");

        // No header → proceed normally (idempotency is opt-in)
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // Try to set the key in Redis (only succeeds if key doesn't exist)
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(redisKey, "processing", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(wasSet)) {
            // Key already exists — this is a duplicate request
            log.warn("Duplicate request detected. Idempotency-Key: {}", idempotencyKey);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":409,\"message\":\"Duplicate request. Idempotency-Key already used: " + idempotencyKey + "\"}"
            );
            return;
        }

        log.debug("Idempotency-Key accepted: {}", idempotencyKey);
        filterChain.doFilter(request, response);
    }
}

