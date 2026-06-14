package com.flexbenefits.repository;

import com.flexbenefits.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    List<Employee> findByTenantId(UUID tenantId);

    Page<Employee> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Employee> findByTenantIdAndEmail(UUID tenantId, String email);
}

