package com.flexbenefits.mapper;

import com.flexbenefits.dto.TenantResponse;
import com.flexbenefits.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);
}

