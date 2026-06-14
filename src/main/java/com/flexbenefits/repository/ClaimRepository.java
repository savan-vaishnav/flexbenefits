package com.flexbenefits.repository;

import com.flexbenefits.entity.Claim;
import com.flexbenefits.entity.enums.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Page<Claim> findByTenantId(UUID tenantId, Pageable pageable);

    List<Claim> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<Claim> findByClaimNumber(String claimNumber);

    List<Claim> findByTenantIdAndStatus(UUID tenantId, ClaimStatus status);
}

