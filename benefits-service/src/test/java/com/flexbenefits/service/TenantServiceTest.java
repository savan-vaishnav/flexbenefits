package com.flexbenefits.service;

import com.flexbenefits.dto.CreateTenantRequest;
import com.flexbenefits.dto.TenantResponse;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.mapper.TenantMapper;
import com.flexbenefits.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Unit Tests")
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantMapper tenantMapper;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantResponse tenantResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme Corporation");
        tenant.setCode("ACME");
        tenant.setContactEmail("admin@acme.com");
        tenant.setActive(true);

        tenantResponse = new TenantResponse(
                tenantId, "Acme Corporation", "ACME",
                "admin@acme.com", true, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("createTenant")
    class CreateTenantTests {

        @Test
        @DisplayName("should create tenant successfully")
        void shouldCreateTenantSuccessfully() {
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Corporation", "acme", "admin@acme.com"
            );

            when(tenantRepository.existsByCode("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
            when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

            TenantResponse result = tenantService.createTenant(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Acme Corporation");
            assertThat(result.code()).isEqualTo("ACME");
            assertThat(result.active()).isTrue();
            verify(tenantRepository).save(any(Tenant.class));
        }

        @Test
        @DisplayName("should uppercase the tenant code")
        void shouldUppercaseCode() {
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Corporation", "acme", "admin@acme.com"
            );

            when(tenantRepository.existsByCode("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant saved = invocation.getArgument(0);
                assertThat(saved.getCode()).isEqualTo("ACME");
                saved.setId(tenantId);
                return saved;
            });
            when(tenantMapper.toResponse(any(Tenant.class))).thenReturn(tenantResponse);

            tenantService.createTenant(request);

            verify(tenantRepository).save(any(Tenant.class));
        }

        @Test
        @DisplayName("should throw IllegalStateException when code already exists")
        void shouldThrowWhenCodeExists() {
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Corporation", "ACME", "admin@acme.com"
            );

            when(tenantRepository.existsByCode("ACME")).thenReturn(true);

            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tenant with code already exists: ACME");
        }
    }

    @Nested
    @DisplayName("getTenantById")
    class GetTenantByIdTests {

        @Test
        @DisplayName("should return tenant when found")
        void shouldReturnTenantWhenFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

            TenantResponse result = tenantService.getTenantById(tenantId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(tenantId);
            assertThat(result.name()).isEqualTo("Acme Corporation");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tenantService.getTenantById(tenantId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Tenant");
        }
    }
}

