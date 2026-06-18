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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BenefitPlanService Unit Tests")
class BenefitPlanServiceTest {

    @Mock private BenefitPlanRepository benefitPlanRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BenefitPlanMapper benefitPlanMapper;

    @InjectMocks
    private BenefitPlanService benefitPlanService;

    private UUID tenantId;
    private UUID planId;
    private Tenant tenant;
    private BenefitPlan plan;
    private BenefitPlanResponse planResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planId = UUID.randomUUID();

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Corp");
        tenant.setCode("ACME");

        plan = new BenefitPlan();
        plan.setId(planId);
        plan.setTenant(tenant);
        plan.setName("Gold Medical");
        plan.setType(PlanType.MEDICAL);
        plan.setCoverageTier(CoverageTier.EMPLOYEE_ONLY);
        plan.setMonthlyPremium(new BigDecimal("500.00"));
        plan.setDeductible(new BigDecimal("1000.00"));
        plan.setMaxCoverage(new BigDecimal("50000.00"));
        plan.setPlanYear(2026);
        plan.setActive(true);

        planResponse = new BenefitPlanResponse(
                planId, tenantId, "Gold Medical", "MEDICAL",
                null, "EMPLOYEE_ONLY",
                new BigDecimal("500.00"), new BigDecimal("1000.00"),
                new BigDecimal("50000.00"), 2026, true, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("createPlan")
    class CreatePlanTests {

        @Test
        @DisplayName("should create plan successfully")
        void shouldCreatePlanSuccessfully() {
            CreateBenefitPlanRequest request = new CreateBenefitPlanRequest(
                    "Gold Medical", "MEDICAL", "Comprehensive medical coverage",
                    "EMPLOYEE_ONLY", new BigDecimal("500.00"),
                    new BigDecimal("1000.00"), new BigDecimal("50000.00"), 2026
            );

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(benefitPlanRepository.save(any(BenefitPlan.class))).thenReturn(plan);
            when(benefitPlanMapper.toResponse(plan)).thenReturn(planResponse);

            BenefitPlanResponse result = benefitPlanService.createPlan(tenantId, request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Gold Medical");
            assertThat(result.type()).isEqualTo("MEDICAL");
            assertThat(result.active()).isTrue();
            verify(benefitPlanRepository).save(any(BenefitPlan.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            CreateBenefitPlanRequest request = new CreateBenefitPlanRequest(
                    "Gold Medical", "MEDICAL", null,
                    "EMPLOYEE_ONLY", new BigDecimal("500.00"),
                    new BigDecimal("1000.00"), new BigDecimal("50000.00"), 2026
            );

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> benefitPlanService.createPlan(tenantId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Tenant");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid plan type")
        void shouldThrowForInvalidPlanType() {
            CreateBenefitPlanRequest request = new CreateBenefitPlanRequest(
                    "Gold Medical", "INVALID_TYPE", null,
                    "EMPLOYEE_ONLY", new BigDecimal("500.00"),
                    new BigDecimal("1000.00"), new BigDecimal("50000.00"), 2026
            );

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThatThrownBy(() -> benefitPlanService.createPlan(tenantId, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getPlans")
    class GetPlansTests {

        @Test
        @DisplayName("should return active plans for tenant")
        void shouldReturnActivePlans() {
            when(benefitPlanRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(plan));
            when(benefitPlanMapper.toResponseList(List.of(plan))).thenReturn(List.of(planResponse));

            List<BenefitPlanResponse> result = benefitPlanService.getPlans(tenantId);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().name()).isEqualTo("Gold Medical");
        }

        @Test
        @DisplayName("should return empty list when no plans exist")
        void shouldReturnEmptyListWhenNoPlans() {
            when(benefitPlanRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of());
            when(benefitPlanMapper.toResponseList(List.of())).thenReturn(List.of());

            List<BenefitPlanResponse> result = benefitPlanService.getPlans(tenantId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPlanById")
    class GetPlanByIdTests {

        @Test
        @DisplayName("should return plan when found and belongs to tenant")
        void shouldReturnPlan() {
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
            when(benefitPlanMapper.toResponse(plan)).thenReturn(planResponse);

            BenefitPlanResponse result = benefitPlanService.getPlanById(tenantId, planId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(planId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when plan not found")
        void shouldThrowWhenPlanNotFound() {
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> benefitPlanService.getPlanById(tenantId, planId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when plan belongs to different tenant")
        void shouldThrowWhenPlanBelongsToDifferentTenant() {
            UUID otherTenantId = UUID.randomUUID();
            when(benefitPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

            assertThatThrownBy(() -> benefitPlanService.getPlanById(otherTenantId, planId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}

