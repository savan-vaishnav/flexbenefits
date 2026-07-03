package com.flexbenefits.mapper;

import com.flexbenefits.dto.BenefitPlanResponse;
import com.flexbenefits.entity.BenefitPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BenefitPlanMapper {

    @Mapping(source = "tenant.id", target = "tenantId")
    BenefitPlanResponse toResponse(BenefitPlan plan);

    List<BenefitPlanResponse> toResponseList(List<BenefitPlan> plans);
}

