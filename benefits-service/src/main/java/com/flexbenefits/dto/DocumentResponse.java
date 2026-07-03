package com.flexbenefits.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID claimId,
        String fileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {}

