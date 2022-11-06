package com.concordeu.auth.service;

import com.concordeu.auth.dao.AuthUserDao;
import com.concordeu.auth.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        Optional<User> user = authUserDao.findByUsername(username);
        log.debug("Trying to load user {}. Successful? {}", username, user.isPresent());
        return user
                .map(this::map)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found:" + username));
    }

    private UserDetails map(User user) {
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), user.getGrantedAuthorities());
    }

}
