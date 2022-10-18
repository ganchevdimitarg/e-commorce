package com.concordeu;

import com.concordeu.domain.AuthUser;
import com.concordeu.dto.AuthUserDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    AuthUserDto mapAuthUserToAuthUserDto(AuthUser authUser);
}
