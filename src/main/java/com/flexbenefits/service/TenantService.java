package com.flexbenefits.service;

import com.flexbenefits.dto.CreateTenantRequest;
import com.flexbenefits.dto.TenantResponse;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.TenantMapper;
import com.flexbenefits.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantResponse createTenant(CreateTenantRequest request) {
        log.info("Creating tenant: {} (code: {})", request.name(), request.code());

        if (tenantRepository.existsByCode(request.code())) {
            throw new IllegalStateException("Tenant with code already exists: " + request.code());
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setCode(request.code().toUpperCase());
        tenant.setContactEmail(request.contactEmail());
        tenant.setActive(true);

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant created: {} with code: {}", saved.getId(), saved.getCode());
        return tenantMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        return tenantMapper.toResponse(tenant);
    }
}

