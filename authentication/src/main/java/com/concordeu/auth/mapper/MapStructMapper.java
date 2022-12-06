package com.concordeu.auth.mapper;

import com.concordeu.auth.domain.AuthUser;
import com.concordeu.auth.dto.AuthUserDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    AuthUserDto mapAuthUserToAuthUserDto(AuthUser authUser);
}
