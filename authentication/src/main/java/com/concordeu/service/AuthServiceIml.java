package com.concordeu.service;

import com.concordeu.MapStructMapper;
import com.concordeu.dao.AuthUserDao;
import com.concordeu.domain.Address;
import com.concordeu.domain.AuthUser;
import com.concordeu.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static com.concordeu.config.security.UserRole.USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceIml implements AuthService {

    private final AuthUserDao authUserDao;
    private final PasswordEncoder passwordEncoder;
    private final MapStructMapper mapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AuthUser> user = authUserDao.findByUsername(username);
        log.debug("Trying to load user {}. Successful? {}", username, user.isPresent());
        return user
                .map(this::map)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("Username \"%s\" not found" + username)));
    }

    private UserDetails map(AuthUser authUser) {
        return new User(authUser.getUsername(), authUser.getPassword(), authUser.getGrantedAuthorities());
    }

    @Override
    public UserDto register(UserDto model) {
        Address address = Address.builder()
                .city(model.getCity())
                .street(model.getStreet())
                .postCode(model.getPostCode())
                .build();

        AuthUser authUser = AuthUser.builder()
                .username(model.getUsername())
                .password(passwordEncoder.encode(model.getPassword()))
                .grantedAuthorities(USER.getGrantedAuthorities())
                .firstName(model.getFirstName())
                .lastName(model.getLastName())
                .address(address)
                .email(model.getEmail())
                .phoneNumber(model.getPhoneNumber())
                .created(LocalDateTime.now())
                .build();

        return mapper.mapUserToUserDto(authUserDao.save(authUser));
    }
}
