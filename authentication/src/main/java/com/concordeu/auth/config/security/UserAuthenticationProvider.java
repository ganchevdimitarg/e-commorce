package com.concordeu.auth.config.security;

import com.concordeu.auth.dao.AuthUserDao;
import com.concordeu.auth.domain.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.CharBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAuthenticationProvider implements AuthenticationProvider {

    private final PasswordEncoder passwordEncoder;
    private final AuthUserDao authUserDao;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        Optional<AuthUser> authUser = authUserDao.findByUsername(username);

        if (authUser.isEmpty()) {
            throw new IllegalArgumentException("User does not exist");
        }

        AuthUser user = authUser.get();

        if (!passwordEncoder.matches(CharBuffer.wrap(password), user.getPassword())) {
            throw new IllegalArgumentException("The password does not match");
        }

        return new UsernamePasswordAuthenticationToken(username, password, user.getGrantedAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.equals(authentication);
    }
}
