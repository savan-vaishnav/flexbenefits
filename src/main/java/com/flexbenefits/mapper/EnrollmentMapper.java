package com.flexbenefits.mapper;

import com.flexbenefits.dto.EnrollmentResponse;
import com.flexbenefits.entity.Enrollment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {

    @Mapping(source = "tenant.id", target = "tenantId")
    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "benefitPlan.id", target = "benefitPlanId")
    EnrollmentResponse toResponse(Enrollment enrollment);

    List<EnrollmentResponse> toResponseList(List<Enrollment> enrollments);
}

