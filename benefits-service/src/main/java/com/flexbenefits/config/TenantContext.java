package com.flexbenefits.config;

import java.util.UUID;

/**
 * Holds the current tenant ID for the request lifecycle.
 * Set by JwtAuthenticationFilter after validating the JWT token.
 * Used by services to scope queries to the authenticated tenant.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

