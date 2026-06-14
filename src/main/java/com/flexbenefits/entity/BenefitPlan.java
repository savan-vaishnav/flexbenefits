package com.flexbenefits.entity;

import com.flexbenefits.entity.enums.CoverageTier;
import com.flexbenefits.entity.enums.PlanType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "benefit_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PlanType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_tier", nullable = false, length = 50)
    private CoverageTier coverageTier;

    @Column(name = "monthly_premium", precision = 10, scale = 2)
    private BigDecimal monthlyPremium;

    @Column(precision = 10, scale = 2)
    private BigDecimal deductible;

    @Column(name = "max_coverage", precision = 12, scale = 2)
    private BigDecimal maxCoverage;

    @Column(name = "plan_year")
    private Integer planYear;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

