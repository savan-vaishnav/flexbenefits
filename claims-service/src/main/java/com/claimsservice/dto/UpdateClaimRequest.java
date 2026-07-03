package com.claimsservice.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateClaimRequest(
        LocalDate serviceDate,
        String providerName,
        String diagnosisCode,
        @Positive BigDecimal claimedAmount
) {}

