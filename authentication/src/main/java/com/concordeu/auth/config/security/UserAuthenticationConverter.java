package com.concordeu.auth.config.security;

import com.concordeu.auth.dto.AuthUserDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class UserAuthenticationConverter implements AuthenticationConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Authentication convert(HttpServletRequest request) {
        AuthUserDto authUserDto;
        try {
            authUserDto = MAPPER.readValue(request.getInputStream(), AuthUserDto.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return new UsernamePasswordAuthenticationToken(authUserDto.username(), authUserDto.password());
    }
}
