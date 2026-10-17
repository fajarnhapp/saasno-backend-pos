package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.RoleMenuEntity;
import com.saasno.utility.dto.role.RoleMenuDTO;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleMenuMapper {

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "menuId", source = "menu.id")
    RoleMenuDTO dto(RoleMenuEntity entity);

    @Mapping(target = "role.id", source = "roleId")
    @Mapping(target = "menu.id", source = "menuId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registered", ignore = true)
    RoleMenuEntity entity(RoleMenuDTO dto);

    List<RoleMenuDTO> dto(List<RoleMenuEntity> entities);

    List<RoleMenuEntity> entity(List<RoleMenuDTO> dtos);
}
