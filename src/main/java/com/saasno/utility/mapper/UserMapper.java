package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.saasno.entity.UserEntity;
import com.saasno.utility.dto.user.UserDTO;

@Mapper(componentModel = "spring", uses = { RoleMapper.class })
public interface UserMapper {

    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    UserDTO dto(UserEntity entity);

    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "status", expression = "java(com.saasno.utility.picker.UserStatus.valueOf(dto.getStatus()))")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "changePassword", ignore = true)
    @Mapping(target = "changePasswordDefault", ignore = true)
    @Mapping(target = "twoFactorAuthSecret", ignore = true)
    @Mapping(target = "failedLoginCount", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "locked", ignore = true)
    UserEntity entity(UserDTO dto);

    List<UserDTO> dto(List<UserEntity> entities);

    List<UserEntity> entity(List<UserDTO> dtos);
}
