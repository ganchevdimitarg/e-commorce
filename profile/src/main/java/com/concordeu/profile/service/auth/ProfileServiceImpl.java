package com.concordeu.profile.service.auth;

import com.concordeu.profile.dao.UserDao;
import com.concordeu.profile.domain.Address;
import com.concordeu.profile.domain.User;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.concordeu.profile.security.UserRole.USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDto createUser(UserRequestDto model) {
        if (userDao.findByUsername(model.username()).isPresent()) {
            throw new InvalidRequestDataException("User already exist: " + model.username());
        }
        Address address = Address.builder()
                .city(model.city())
                .street(model.street())
                .postCode(model.postCode())
                .build();

        User authUser = User.builder()
                .username(model.username())
                .password(passwordEncoder.encode(model.password().trim().isEmpty() ? "" : model.password().trim()))
                .grantedAuthorities(USER.getGrantedAuthorities())
                .firstName(model.firstName())
                .lastName(model.lastName())
                .address(address)
                .phoneNumber(model.phoneNumber())
                .created(LocalDateTime.now())
                .build();

        User user = userDao.insert(authUser);
        log.info("The user was successfully create");
        return getUserDto(user);
    }

    @Override
    public UserDto getOrCreateUser(String username) {
        Assert.hasLength(username, "Username is empty!");
        Optional<User> user = userDao.findByUsername(username);

        return getUserDto(user.orElseGet(() -> createUserWithEmail(username)));
    }

    @Override
    public void updateUser(String username, UserRequestDto requestDto) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        user.setUsername(requestDto.username());
        user.setPassword(requestDto.password());
        user.setFirstName(requestDto.firstName());
        user.setLastName(requestDto.lastName());
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

        userDao.save(user);
        log.info("User with username {} is update", user.getUsername());
    }

    @Override
    public void deleteUser(String username) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        userDao.delete(user);
    }

    @Override
    public UserDto getUserByEmail(String username) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(()-> new UsernameNotFoundException("User does not exist"));
        user.setPassword("");
        return getUserDto(user);
    }

    private User createUserWithEmail(String username) {
        log.info("Create user with username");
        User user = User.builder()
                .username(username)
                .password("")
                .grantedAuthorities(USER.getGrantedAuthorities())
                .created(LocalDateTime.now())
                .build();
        return userDao.insert(user);
    }

    private UserDto getUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getGrantedAuthorities(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getAddress().getCity(),
                user.getAddress().getStreet(),
                user.getAddress().getPostCode());
    }
}
