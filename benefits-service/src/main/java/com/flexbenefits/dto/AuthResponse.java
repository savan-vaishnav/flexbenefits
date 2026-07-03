package com.flexbenefits.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        UUID tenantId,
        String email,
        String role
) {}

