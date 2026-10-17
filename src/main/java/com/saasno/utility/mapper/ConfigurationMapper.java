package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.utility.dto.configuration.ConfigurationDTO;

@Mapper(componentModel = "spring")
public interface ConfigurationMapper {

    ConfigurationDTO dto(ConfigurationEntity entity);

    ConfigurationEntity entity(ConfigurationDTO dto);

    List<ConfigurationDTO> dto(List<ConfigurationEntity> entities);

    List<ConfigurationEntity> entity(List<ConfigurationDTO> dtos);
}
