package com.concordeu.client.common.dto;

import com.concordeu.client.common.ProfileGrantedAuthority;
import lombok.Builder;

import java.util.Set;

@Builder
public record AuthUserDto (
        String username,
        String password,
        Set<ProfileGrantedAuthority> grantedAuthorities){
}
