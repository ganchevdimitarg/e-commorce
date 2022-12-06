package com.concordeu.auth.service.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.concordeu.auth.config.jwt.JwtConfiguration;
import com.concordeu.auth.config.jwt.JwtSecretKey;
import com.concordeu.auth.dao.AuthUserDao;
import com.concordeu.auth.domain.Address;
import com.concordeu.auth.domain.AuthUser;
import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;
import com.concordeu.auth.excaption.InvalidRequestDataException;
import com.concordeu.auth.mapper.MapStructMapper;
import com.concordeu.auth.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.concordeu.auth.security.UserRole.USER;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService{
    private final AuthUserDao authUserDao;
    private final PasswordEncoder passwordEncoder;
    private final MapStructMapper mapper;
    private final JwtSecretKey jwtSecretKey;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtConfiguration jwtConfiguration;

    @Override
    public AuthUserDto createUser(AuthUserRequestDto model) {
        if (authUserDao.findByEmail(model.email()).isPresent()) {
            throw new InvalidRequestDataException("User already exist: " + model.email());
        }
        Address address = Address.builder()
                .city(model.city())
                .street(model.street())
                .postCode(model.postCode())
                .build();

        AuthUser authUser = AuthUser.builder()
                .username(model.username())
                .password(passwordEncoder.encode(model.password().trim().isEmpty() ? "" : model.password().trim()))
                .grantedAuthorities(USER.getGrantedAuthorities())
                .firstName(model.firstName())
                .lastName(model.lastName())
                .address(address)
                .email(model.email())
                .phoneNumber(model.phoneNumber())
                .created(LocalDateTime.now())
                .build();

        AuthUser user = authUserDao.insert(authUser);
        log.info("The user was successfully create");
        return mapper.mapAuthUserToAuthUserDto(user);
    }

    @Override
    public AuthUserDto getOrCreateUser(String email) {
        Assert.hasLength(email, "Email is empty!");
        Optional<AuthUser> user = authUserDao.findByEmail(email);

        return mapper.mapAuthUserToAuthUserDto(user.orElseGet(() -> createUserWithEmail(email)));
    }

    @Override
    public void updateUser(String email, AuthUserRequestDto requestDto) {
        Assert.hasLength(email, "Email is empty");
        AuthUser user = authUserDao.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        user.setUsername(requestDto.username());
        user.setPassword(requestDto.password());
        user.setFirstName(requestDto.firstName());
        user.setLastName(requestDto.lastName());
        user.setEmail(requestDto.email());
        user.setPhoneNumber(requestDto.phoneNumber());
        if (user.getAddress() == null) {
            Address address = Address.builder()
                    .city(requestDto.city())
                    .street(requestDto.street())
                    .postCode(requestDto.postCode())
                    .build();
            user.setAddress(address);
        } else {
            user.getAddress().setCity(requestDto.city());
            user.getAddress().setStreet(requestDto.street());
            user.getAddress().setPostCode(requestDto.postCode());
        }

        authUserDao.save(user);
        log.info("User with email {} is update", user.getEmail());
    }

    @Override
    public void deleteUser(String email) {
        Assert.hasLength(email, "Email is empty");
        AuthUser user = authUserDao.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        authUserDao.delete(user);
    }

    @Override
    public AuthUserDto getUserByEmail(String email) {
        Assert.hasLength(email, "Email is empty");
        AuthUser user = authUserDao.findByEmail(email)
                .orElseThrow(()-> new UsernameNotFoundException("User does not exist"));
        user.setPassword("");
        return mapper.mapAuthUserToAuthUserDto(user);
    }

    @Override
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorizationHeader = request.getHeader(AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            try {
                String refresh_token = authorizationHeader.replace(jwtConfiguration.getTokenPrefix(), "");
                JWTVerifier verifier = JWT.require(jwtSecretKey.secretKey()).build();
                DecodedJWT decodedJWT = verifier.verify(refresh_token);
                String username = decodedJWT.getSubject();
                AuthUser userDto = authUserDao.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException("User does not exist"));
                User user = new User(userDto.getUsername(), userDto.getPassword(), userDto.getGrantedAuthorities());
                String access_token = jwtTokenUtil.generateJwtToken(request, user, 2);
                jwtTokenUtil.setResponseWithJwts(response, access_token, refresh_token);
                log.info("Generate new access token to: {}", username);

            } catch (JWTVerificationException ex) {
                jwtTokenUtil.setErrorHeader(response, ex);
            }
        }else {
            throw new RuntimeException("Refresh token is missing");
        }
    }

    private AuthUser createUserWithEmail(String email) {
        log.info("Create user with email");
        AuthUser user = AuthUser.builder()
                .email(email)
                .password("")
                .grantedAuthorities(USER.getGrantedAuthorities())
                .created(LocalDateTime.now())
                .build();
        return authUserDao.insert(user);
    }


}
