package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.saasno.entity.MenuEntity;
import com.saasno.utility.dto.menu.MenuDTO;

@Mapper(componentModel = "spring")
public interface MenuMapper {

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "parentId", source = "parent.id")
    MenuDTO dto(MenuEntity entity);

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "parent.id", source = "parentId")
    MenuEntity entity(MenuDTO dto);

    List<MenuDTO> dto(List<MenuEntity> entities);

    List<MenuEntity> entity(List<MenuDTO> dtos);
}
