package com.concordeu.auth.service.securiy;

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
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<AuthUser> user = authUserDao.findByEmail(email);
        log.debug("Trying to load user {}. Successful? {}", email, user.isPresent());
        return user
                .map(this::map)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found:" + email));
    }

    private UserDetails map(AuthUser authUser) {
        return new User(authUser.getEmail(), authUser.getPassword(), authUser.getGrantedAuthorities());
    }
}
