package com.flexbenefits.service;

import com.flexbenefits.dto.BenefitPlanResponse;
import com.flexbenefits.dto.CreateBenefitPlanRequest;
import com.flexbenefits.entity.BenefitPlan;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.entity.enums.CoverageTier;
import com.flexbenefits.entity.enums.PlanType;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.BenefitPlanMapper;
import com.flexbenefits.repository.BenefitPlanRepository;
import com.flexbenefits.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BenefitPlanService {

    private final BenefitPlanRepository benefitPlanRepository;
    private final TenantRepository tenantRepository;
    private final BenefitPlanMapper benefitPlanMapper;

    @CacheEvict(value = "benefit-plans", key = "#tenantId")
    public BenefitPlanResponse createPlan(UUID tenantId, CreateBenefitPlanRequest request) {
        log.info("Creating benefit plan '{}' for tenant: {}", request.name(), tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        BenefitPlan plan = new BenefitPlan();
        plan.setTenant(tenant);
        plan.setName(request.name());
        plan.setType(PlanType.valueOf(request.type()));
        plan.setDescription(request.description());
        plan.setCoverageTier(CoverageTier.valueOf(request.coverageTier()));
        plan.setMonthlyPremium(request.monthlyPremium());
        plan.setDeductible(request.deductible());
        plan.setMaxCoverage(request.maxCoverage());
        plan.setPlanYear(request.planYear());
        plan.setActive(true);

        BenefitPlan saved = benefitPlanRepository.save(plan);
        log.info("Benefit plan created: {} for tenant: {} (plan list cache evicted)", saved.getId(), tenantId);
        return benefitPlanMapper.toResponse(saved);
    }

    @Cacheable(value = "benefit-plans", key = "#tenantId")
    @Transactional(readOnly = true)
    public List<BenefitPlanResponse> getPlans(UUID tenantId) {
        log.info("Cache MISS — loading benefit plans from DB for tenant: {}", tenantId);
        List<BenefitPlan> plans = benefitPlanRepository.findByTenantIdAndActiveTrue(tenantId);
        return benefitPlanMapper.toResponseList(plans);
    }

    @Cacheable(value = "benefit-plan", key = "#planId")
    @Transactional(readOnly = true)
    public BenefitPlanResponse getPlanById(UUID tenantId, UUID planId) {
        log.info("Cache MISS — loading benefit plan {} from DB", planId);
        BenefitPlan plan = benefitPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPlan", planId));

        if (!plan.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("BenefitPlan", planId);
        }

        return benefitPlanMapper.toResponse(plan);
    }
}

