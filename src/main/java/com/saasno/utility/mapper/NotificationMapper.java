package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.saasno.entity.NotificationEntity;
import com.saasno.utility.dto.notification.NotificationDTO;

@Mapper(componentModel = "spring", uses = { NotificationMessageMapper.class })
public interface NotificationMapper {

    @Mapping(target = "notificationMessage", source = "notificationMessage")
    NotificationDTO dto(NotificationEntity entity);

    @Mapping(target = "notificationMessage", source = "notificationMessage")
    NotificationEntity entity(NotificationDTO dto);

    List<NotificationDTO> dto(List<NotificationEntity> entities);

    List<NotificationEntity> entity(List<NotificationDTO> dtos);
}
