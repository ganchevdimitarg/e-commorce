package com.concordeu.profile.mapper;

import com.concordeu.profile.domain.User;
import com.concordeu.profile.dto.UserDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    UserDto mapAuthUserToAuthUserDto(User user);
}
