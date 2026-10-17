package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.saasno.entity.DownloadJobEntity;
import com.saasno.utility.dto.download.DownloadJobDTO;

@Mapper(componentModel = "spring")
public interface DownloadJobMapper {

    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    DownloadJobDTO dto(DownloadJobEntity entity);

    List<DownloadJobDTO> dto(List<DownloadJobEntity> entities);
}
