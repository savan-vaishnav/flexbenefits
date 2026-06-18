package com.flexbenefits.repository;

import com.flexbenefits.entity.Enrollment;
import com.flexbenefits.entity.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findByTenantId(UUID tenantId);

    List<Enrollment> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    boolean existsByEmployeeIdAndBenefitPlanIdAndStatusNot(UUID employeeId, UUID planId, EnrollmentStatus status);
}

