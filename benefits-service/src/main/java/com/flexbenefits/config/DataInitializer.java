package com.flexbenefits.config;

import com.flexbenefits.entity.*;
import com.flexbenefits.entity.enums.CoverageTier;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import com.flexbenefits.entity.enums.PlanType;
import com.flexbenefits.entity.enums.Role;
import com.flexbenefits.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds the database with demo data on first startup so the system is immediately testable.
 *
 * After docker compose up, you can:
 *   1. Login as admin:    POST /api/v1/auth/login  { "email": "admin@demo.com", "password": "admin123" }
 *   2. List plans:        GET  /api/v1/plans
 *   3. List enrollments:  GET  /api/v1/enrollments
 *   4. Create a claim:    POST /api/v1/claims  (use enrollmentId from step 3)
 *
 * This runner is IDEMPOTENT — it only creates data if it doesn't already exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BenefitPlanRepository benefitPlanRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPlatformTenantAndSuperAdmin();
        seedDemoCompany();
    }

    private void seedPlatformTenantAndSuperAdmin() {
        Tenant platformTenant = tenantRepository.findByCode("PLATFORM")
                .orElseGet(() -> {
                    Tenant tenant = new Tenant();
                    tenant.setName("FlexBenefits Platform");
                    tenant.setCode("PLATFORM");
                    tenant.setContactEmail("platform@flexbenefits.com");
                    tenant.setActive(true);
                    Tenant saved = tenantRepository.save(tenant);
                    log.info("Seeded PLATFORM tenant: {}", saved.getId());
                    return saved;
                });

        if (!userRepository.existsByTenantIdAndEmail(platformTenant.getId(), "superadmin@flexbenefits.com")) {
            User superAdmin = User.builder()
                    .tenant(platformTenant)
                    .email("superadmin@flexbenefits.com")
                    .password(passwordEncoder.encode("changeme123"))
                    .firstName("Super")
                    .lastName("Admin")
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .build();
            userRepository.save(superAdmin);
            log.info("Seeded SUPER_ADMIN: superadmin@flexbenefits.com / changeme123");
        }
    }

    private void seedDemoCompany() {
        if (tenantRepository.findByCode("DEMO").isPresent()) {
            log.info("Demo company already exists — skipping seed.");
            return;
        }

        // 1. Create demo tenant
        Tenant demo = new Tenant();
        demo.setName("Demo Corp");
        demo.setCode("DEMO");
        demo.setContactEmail("hr@demo.com");
        demo.setActive(true);
        demo = tenantRepository.save(demo);
        log.info("Seeded demo tenant: {} ({})", demo.getName(), demo.getId());

        // 2. Create HR admin user (for API testing)
        User admin = User.builder()
                .tenant(demo)
                .email("admin@demo.com")
                .password(passwordEncoder.encode("admin123"))
                .firstName("HR")
                .lastName("Admin")
                .role(Role.HR_ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("Seeded HR admin: admin@demo.com / admin123");

        // 3. Create a demo employee
        Employee employee = new Employee();
        employee.setTenant(demo);
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john.doe@demo.com");
        employee.setEmployeeCode("EMP-001");
        employee.setDateOfBirth(LocalDate.of(1990, 5, 15));
        employee.setHireDate(LocalDate.of(2023, 1, 10));
        employee.setActive(true);
        employee = employeeRepository.save(employee);
        log.info("Seeded employee: {} {} ({})", employee.getFirstName(), employee.getLastName(), employee.getId());

        // 4. Create benefit plans
        BenefitPlan medical = createPlan(demo, "Premium Medical Plan", PlanType.MEDICAL,
                CoverageTier.EMPLOYEE_FAMILY, "500.00", "1000.00", "500000.00",
                "Comprehensive medical coverage including hospitalization and outpatient care");
        BenefitPlan dental = createPlan(demo, "Dental Care Plan", PlanType.DENTAL,
                CoverageTier.EMPLOYEE_ONLY, "75.00", "200.00", "50000.00",
                "Dental coverage including preventive care and major procedures");
        createPlan(demo, "Vision Plan", PlanType.VISION,
                CoverageTier.EMPLOYEE_SPOUSE, "30.00", "100.00", "25000.00",
                "Vision coverage for eye exams and corrective lenses");

        // 5. Create an active enrollment (so claims can be created immediately)
        Enrollment enrollment = new Enrollment();
        enrollment.setTenant(demo);
        enrollment.setEmployee(employee);
        enrollment.setBenefitPlan(medical);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrollmentDate(LocalDate.now().minusMonths(3));
        enrollment.setEffectiveDate(LocalDate.now().minusMonths(2));
        enrollment = enrollmentRepository.save(enrollment);
        log.info("Seeded enrollment: {} -> {} ({})", employee.getEmail(), medical.getName(), enrollment.getId());

        Enrollment dentalEnrollment = new Enrollment();
        dentalEnrollment.setTenant(demo);
        dentalEnrollment.setEmployee(employee);
        dentalEnrollment.setBenefitPlan(dental);
        dentalEnrollment.setStatus(EnrollmentStatus.ACTIVE);
        dentalEnrollment.setEnrollmentDate(LocalDate.now().minusMonths(3));
        dentalEnrollment.setEffectiveDate(LocalDate.now().minusMonths(2));
        enrollmentRepository.save(dentalEnrollment);

        log.info("=== Demo data ready! Login: admin@demo.com / admin123 ===");
    }

    private BenefitPlan createPlan(Tenant tenant, String name, PlanType type,
                                    CoverageTier tier, String premium, String deductible,
                                    String maxCoverage, String description) {
        BenefitPlan plan = new BenefitPlan();
        plan.setTenant(tenant);
        plan.setName(name);
        plan.setType(type);
        plan.setCoverageTier(tier);
        plan.setMonthlyPremium(new BigDecimal(premium));
        plan.setDeductible(new BigDecimal(deductible));
        plan.setMaxCoverage(new BigDecimal(maxCoverage));
        plan.setDescription(description);
        plan.setPlanYear(LocalDate.now().getYear());
        plan.setActive(true);
        BenefitPlan saved = benefitPlanRepository.save(plan);
        log.info("Seeded plan: {} ({})", saved.getName(), saved.getId());
        return saved;
    }
}

