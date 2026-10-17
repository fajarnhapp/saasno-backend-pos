package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.LogEntity;
import com.saasno.utility.dto.log.LogDTO;

@Mapper(componentModel = "spring")
public interface LogMapper {

    LogDTO dto(LogEntity entity);

    List<LogDTO> dto(List<LogEntity> entities);
}
