package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.NotificationUnreadEntity;
import com.saasno.utility.dto.notification.NotificationUnreadDTO;

@Mapper(componentModel = "spring")
public interface NotificationUnreadMapper {

    NotificationUnreadDTO dto(NotificationUnreadEntity entity);

    NotificationUnreadEntity entity(NotificationUnreadDTO dto);

    List<NotificationUnreadDTO> dto(List<NotificationUnreadEntity> entities);

    List<NotificationUnreadEntity> entity(List<NotificationUnreadDTO> dtos);
}
