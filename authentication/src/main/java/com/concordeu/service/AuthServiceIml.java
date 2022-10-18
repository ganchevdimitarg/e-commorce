package com.concordeu.service;

import com.concordeu.MapStructMapper;
import com.concordeu.dao.AuthUserDao;
import com.concordeu.domain.Address;
import com.concordeu.domain.AuthUser;
import com.concordeu.dto.AuthUserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.concordeu.security.UserRole.USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceIml implements AuthService {

    private final AuthUserDao authUserDao;
    private final PasswordEncoder passwordEncoder;
    private final MapStructMapper mapper;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<AuthUser> user = authUserDao.findByEmail(email);
        log.debug("Trying to load user {}. Successful? {}", email, user.isPresent());
        return user
                .map(this::map)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("Username \"%s\" not found" + email)));
    }

    private UserDetails map(AuthUser authUser) {
        return new User(authUser.getEmail(), authUser.getPassword(), authUser.getGrantedAuthorities());
    }

    @Override
    public AuthUserDto createUser(AuthUserDto model) {
        Address address = Address.builder()
                .city(model.getCity())
                .street(model.getStreet())
                .postCode(model.getPostCode())
                .build();

        AuthUser authUser = AuthUser.builder()
                .username(model.getUsername())
                .password(passwordEncoder.encode(model.getPassword().trim().isEmpty() ? "" : model.getPassword().trim()))
                .grantedAuthorities(USER.getGrantedAuthorities())
                .firstName(model.getFirstName())
                .lastName(model.getLastName())
                .address(address)
                .email(model.getEmail())
                .phoneNumber(model.getPhoneNumber())
                .created(LocalDateTime.now())
                .build();

        return mapper.mapAuthUserToAuthUserDto(authUserDao.save(authUser));
    }

    @Override
    public AuthUserDto getOrCreateUser(String email) {
        Assert.notNull(email, "Email is empty!");
        Optional<AuthUser> user = authUserDao.findByEmail(email);

        return mapper.mapAuthUserToAuthUserDto(user.orElseGet(() -> createUserWithEmail(email)));
    }

    private AuthUser createUserWithEmail(String email) {
        log.info("Create user with email");
        AuthUser user = AuthUser.builder()
                .email(email)
                .password("")
                .grantedAuthorities(USER.getGrantedAuthorities())
                .build();
        return authUserDao.save(user);
    }


}
