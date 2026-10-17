package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.RoleEntity;
import com.saasno.utility.dto.role.RoleDTO;

@Mapper(componentModel = "spring", uses = { MenuMapper.class, ApiMapper.class })
public interface RoleMapper {

    RoleDTO dto(RoleEntity entity);

    RoleEntity entity(RoleDTO dto);

    List<RoleDTO> dto(List<RoleEntity> entities);

    List<RoleEntity> entity(List<RoleDTO> dtos);
}
