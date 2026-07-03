package com.flexbenefits.repository;

import com.flexbenefits.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Minimal repository — only used by DocumentService to verify claim ownership.
 * Full claim CRUD is in claims-service.
 */
@Repository
public interface ClaimRepository extends JpaRepository<Claim, UUID> {
}
