package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.NotificationMessageEntity;
import com.saasno.utility.dto.notification.NotificationMessageDTO;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMessageMapper {

    @Mapping(target = "modificated", ignore = true)
    NotificationMessageDTO dto(NotificationMessageEntity entity);

    NotificationMessageEntity entity(NotificationMessageDTO dto);

    List<NotificationMessageDTO> dto(List<NotificationMessageEntity> entities);

    List<NotificationMessageEntity> entity(List<NotificationMessageDTO> dtos);
}