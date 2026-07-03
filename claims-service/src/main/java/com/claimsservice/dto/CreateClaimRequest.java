package com.claimsservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClaimRequest(
        @NotNull UUID employeeId,
        @NotNull UUID enrollmentId,
        @NotNull LocalDate serviceDate,
        @NotBlank String providerName,
        String diagnosisCode,
        @NotNull @Positive BigDecimal claimedAmount
) {}

