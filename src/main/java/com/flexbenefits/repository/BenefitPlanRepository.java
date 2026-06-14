package com.flexbenefits.repository;

import com.flexbenefits.entity.BenefitPlan;
import com.flexbenefits.entity.enums.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BenefitPlanRepository extends JpaRepository<BenefitPlan, UUID> {

    List<BenefitPlan> findByTenantIdAndActiveTrue(UUID tenantId);

    List<BenefitPlan> findByTenantIdAndType(UUID tenantId, PlanType type);
}

