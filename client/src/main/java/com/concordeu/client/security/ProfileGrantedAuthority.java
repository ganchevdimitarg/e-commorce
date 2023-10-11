package com.concordeu.client.security;

import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@AllArgsConstructor
public class ProfileGrantedAuthority implements GrantedAuthority {
    private String authority;
    @Override
    public String getAuthority() {
        return this.authority;
    }
}
