package com.concordeu.auth.service;

import com.concordeu.auth.repository.AuthUserRepository;
import com.concordeu.auth.entities.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final AuthUserRepository AuthUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUser user = AuthUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return new User(user.getUsername(), user.getPassword(), user.getGrantedAuthorities());
    }
}
