package com.flexbenefits.repository;

import com.flexbenefits.entity.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    List<ClaimDocument> findByClaimIdAndTenantId(UUID claimId, UUID tenantId);

    Optional<ClaimDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByClaimId(UUID claimId);
}

