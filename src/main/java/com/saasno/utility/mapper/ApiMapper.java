package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.ApiEntity;
import com.saasno.utility.dto.api.ApiDTO;

@Mapper(componentModel = "spring")
public interface ApiMapper {

    ApiDTO dto(ApiEntity entity);

    ApiEntity entity(ApiDTO dto);

    List<ApiDTO> dto(List<ApiEntity> entities);

    List<ApiEntity> entity(List<ApiDTO> dtos);
}
