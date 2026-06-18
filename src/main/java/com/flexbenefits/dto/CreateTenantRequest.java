package com.flexbenefits.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank String name,
        @NotBlank String code,
        String contactEmail
) {}

