package com.flexbenefits.controller;

import com.flexbenefits.config.TenantContext;
import com.flexbenefits.dto.BenefitPlanResponse;
import com.flexbenefits.dto.CreateBenefitPlanRequest;
import com.flexbenefits.service.BenefitPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class BenefitPlanController {

    private final BenefitPlanService benefitPlanService;

    @PostMapping
    public ResponseEntity<BenefitPlanResponse> createPlan(
            @Valid @RequestBody CreateBenefitPlanRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        BenefitPlanResponse response = benefitPlanService.createPlan(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BenefitPlanResponse>> getPlans() {
        UUID tenantId = TenantContext.getTenantId();
        List<BenefitPlanResponse> plans = benefitPlanService.getPlans(tenantId);
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BenefitPlanResponse> getPlanById(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        BenefitPlanResponse response = benefitPlanService.getPlanById(tenantId, id);
        return ResponseEntity.ok(response);
    }
}

