package com.concordeu.profile.controller;

import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.profile.annotation.ValidationRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {
    private static final String ADMIN = "admin";

    private final ProfileService profileService;
    private final MailService mailService;

    @PostMapping("/register-admin")
    @ValidationRequest
    public Mono<UserDto> createAdmin(@RequestBody UserRequestDto requestDto) {
        return profileService.createAdmin(requestDto);
    }

    @PostMapping("/register-worker")
    @ValidationRequest
    public Mono<UserDto> createWorker(@RequestBody UserRequestDto requestDto) {
        return profileService.createWorker(requestDto);
    }

    @PostMapping("/register-user")
    @ValidationRequest
    public Mono<UserDto> createUser(@RequestBody UserRequestDto requestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        mailService.sendUserWelcomeMail(requestDto.username());
        return profileService.createUser(requestDto);
    }

    @GetMapping("/get-by-username")
    public Mono<UserDto> getUserByUsername(Authentication authentication, @RequestParam String username) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        String principalName = authentication.getName();

        if (principalName.equals(username.trim()) ||
                principalName.equals(ADMIN) ||
                principalName.equals("gateway")) {
            return profileService.getUserByUsername(username.trim());
        }
        log.debug("Profile '{}' try to access another account '{}'", principalName, username);
        throw new IllegalArgumentException();
    }

    @PutMapping("/update-user")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public Mono<Void> updateUser(@RequestBody UserRequestDto requestDto,
                           @RequestParam String username) {
        profileService.updateUser(username.trim(), requestDto);
        return Mono.empty();
    }

    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAuthority('SCOPE_profile.write')")
    public Mono<Void> deleteUser(Authentication authentication) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        profileService.deleteUser(authentication.getName().trim());
        return Mono.empty();
    }

    @GetMapping("/password-reset")
    public Mono<String> passwordReset(@RequestParam String username) {
        return Mono.just(String.format("""
                token: %s
                """, profileService.passwordReset(username.trim()).toFuture().getNow("")));
    }

    @GetMapping("/password-reset-token")
    public Mono<Boolean> isValidPasswordReset(@RequestParam String token) {
        return Mono.just(profileService.isPasswordResetTokenValid(token.trim()));
    }

    @GetMapping("/set-new-password")
    public Mono<Void> setNewPassword(@RequestParam String username, @RequestParam String password) {
        profileService.setNewPassword(username.trim(), password.trim());
        return Mono.empty();
    }
}
