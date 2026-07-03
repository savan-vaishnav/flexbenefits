package com.claimsservice.controller;

import com.claimsservice.dto.ClaimResponse;
import com.claimsservice.dto.CreateClaimRequest;
import com.claimsservice.dto.UpdateClaimRequest;
import com.claimsservice.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<ClaimResponse> createClaim(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateClaimRequest request) {
        ClaimResponse response = claimService.createClaim(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ClaimResponse>> getClaims(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            Pageable pageable) {
        Page<ClaimResponse> claims = claimService.getClaims(tenantId, pageable);
        return ResponseEntity.ok(claims);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaimById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        ClaimResponse response = claimService.getClaimById(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimResponse> updateClaim(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClaimRequest request) {
        ClaimResponse response = claimService.updateClaim(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClaim(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        claimService.deleteClaim(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/submit")
    public ResponseEntity<ClaimResponse> submitClaim(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        ClaimResponse response = claimService.submitClaim(tenantId, id);
        return ResponseEntity.ok(response);
    }
}

