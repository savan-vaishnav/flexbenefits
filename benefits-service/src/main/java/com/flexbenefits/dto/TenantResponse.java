package com.flexbenefits.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String code,
        String contactEmail,
        boolean active,
        LocalDateTime createdAt
) {}

