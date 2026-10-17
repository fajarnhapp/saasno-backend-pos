package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.RoleApiEntity;
import com.saasno.utility.dto.role.RoleApiDTO;

import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleApiMapper {

    @Mapping(target = "apiId", source = "api.id")
    @Mapping(target = "roleId", source = "role.id")
    RoleApiDTO dto(RoleApiEntity entity);

    @Mapping(target = "api.id", source = "apiId")
    @Mapping(target = "role.id", source = "roleId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registered", ignore = true)
    RoleApiEntity entity(RoleApiDTO dto);

    List<RoleApiDTO> dto(List<RoleApiEntity> entities);

    List<RoleApiEntity> entity(List<RoleApiDTO> dtos);
}
