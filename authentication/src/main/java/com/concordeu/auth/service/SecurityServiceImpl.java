package com.concordeu.auth.service;

import com.concordeu.auth.dao.AuthUserDao;
import com.concordeu.auth.domain.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityServiceImpl implements SecurityService {

    private final AuthUserDao authUserDao;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AuthUser> user = authUserDao.findByUsername(username);
        log.debug("Trying to load user {}. Successful? {}", username, user.isPresent());
        return user
                .map(this::map)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found:" + username));
    }

    private UserDetails map(AuthUser authUser) {
        return new User(authUser.getUsername(), authUser.getPassword(), authUser.getGrantedAuthorities());
    }

}
