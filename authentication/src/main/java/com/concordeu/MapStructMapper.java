package com.concordeu;

import com.concordeu.domain.AuthUser;
import com.concordeu.dto.UserDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    UserDto mapUserToUserDto(AuthUser authUser);
}
