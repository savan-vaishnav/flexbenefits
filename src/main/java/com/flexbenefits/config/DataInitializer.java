package com.flexbenefits.config;

import com.flexbenefits.entity.Tenant;
import com.flexbenefits.entity.User;
import com.flexbenefits.entity.enums.Role;
import com.flexbenefits.repository.TenantRepository;
import com.flexbenefits.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the database with the PLATFORM tenant and Super Admin user on first startup.
 *
 * This solves the chicken-and-egg problem:
 *   - You can't create a tenant via API without being authenticated
 *   - You can't register a user without a tenant existing first
 *   - So the very first Super Admin + its tenant must be seeded automatically
 *
 * The Super Admin can then:
 *   1. Login via POST /api/v1/auth/login
 *   2. Create new company tenants via POST /api/v1/tenants
 *
 * This runner is IDEMPOTENT — it only creates the seed data if it doesn't already exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String PLATFORM_TENANT_CODE = "PLATFORM";
    private static final String SUPER_ADMIN_EMAIL = "superadmin@flexbenefits.com";
    private static final String SUPER_ADMIN_DEFAULT_PASSWORD = "changeme123";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPlatformTenantAndSuperAdmin();
    }

    private void seedPlatformTenantAndSuperAdmin() {
        // Step 1: Create the PLATFORM tenant if it doesn't exist
        Tenant platformTenant = tenantRepository.findByCode(PLATFORM_TENANT_CODE)
                .orElseGet(() -> {
                    Tenant tenant = new Tenant();
                    tenant.setName("FlexBenefits Platform");
                    tenant.setCode(PLATFORM_TENANT_CODE);
                    tenant.setContactEmail("platform@flexbenefits.com");
                    tenant.setActive(true);
                    Tenant saved = tenantRepository.save(tenant);
                    log.info("✅ Seeded PLATFORM tenant with id: {}", saved.getId());
                    return saved;
                });

        // Step 2: Create the Super Admin user if it doesn't exist
        if (!userRepository.existsByTenantIdAndEmail(platformTenant.getId(), SUPER_ADMIN_EMAIL)) {
            User superAdmin = User.builder()
                    .tenant(platformTenant)
                    .email(SUPER_ADMIN_EMAIL)
                    .password(passwordEncoder.encode(SUPER_ADMIN_DEFAULT_PASSWORD))
                    .firstName("Super")
                    .lastName("Admin")
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .build();

            User saved = userRepository.save(superAdmin);
            log.info("✅ Seeded SUPER_ADMIN user: {} (id: {})", saved.getEmail(), saved.getId());
            log.warn("⚠️  Default Super Admin password is '{}' — change it after first login!", SUPER_ADMIN_DEFAULT_PASSWORD);
        } else {
            log.info("PLATFORM tenant and SUPER_ADMIN already exist — skipping seed.");
        }
    }
}

