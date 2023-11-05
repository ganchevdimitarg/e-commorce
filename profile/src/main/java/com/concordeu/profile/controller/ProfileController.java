package com.concordeu.profile.controller;

import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.profile.annotation.ValidationRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.observation.annotation.Observed;
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

    @GetMapping("/test")
    public String test() {
        return "Tt's working";
    }

    @PostMapping("/register-worker")
    @ValidationRequest
    @Observed(
            name = "user.name",
            contextualName = "createWorker",
            lowCardinalityKeyValues = {"method", "createWorker"}
    )
    public UserDto createWorker(@RequestBody UserRequestDto requestDto) {
        return profileService.createWorker(requestDto);
    }

    @PostMapping("/register-user")
    @ValidationRequest
    @Observed(
            name = "user.name",
            contextualName = "createUser",
            lowCardinalityKeyValues = {"method", "createUser"}
    )
    public UserDto createUser(@RequestBody UserRequestDto requestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        mailService.sendUserWelcomeMail(requestDto.username());
        return profileService.createUser(requestDto);
    }

    @GetMapping("/get-by-username")
    @Observed(
            name = "user.name",
            contextualName = "getUserByUsername",
            lowCardinalityKeyValues = {"method", "getUserByUsername"}
    )
    public UserDto getUserByUsername(Authentication authentication, @RequestParam String username) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
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
    @Observed(
            name = "user.name",
            contextualName = "updateUser",
            lowCardinalityKeyValues = {"method", "updateUser"}
    )
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @RequestParam String username) {
        profileService.updateUser(username.trim(), requestDto);
    }

    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAuthority('SCOPE_profile.write')")
    @Observed(
            name = "user.name",
            contextualName = "deleteUser",
            lowCardinalityKeyValues = {"method", "deleteUser"}
    )
    public void deleteUser(Authentication authentication) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        profileService.deleteUser(authentication.getName().trim());
    }

    @GetMapping("/password-reset")
    @Observed(
            name = "user.name",
            contextualName = "passwordReset",
            lowCardinalityKeyValues = {"method", "passwordReset"}
    )
    public String passwordReset(@RequestParam String username) {
        return String.format("""
                token: %s
                """, profileService.passwordReset(username.trim()));
    }

    @GetMapping("/password-reset-token")
    @Observed(
            name = "user.name",
            contextualName = "isPasswordResetTokenValid",
            lowCardinalityKeyValues = {"method", "isPasswordResetTokenValid"}
    )
    public boolean isValidPasswordReset(@RequestParam String token) {
        return profileService.isPasswordResetTokenValid(token.trim());
    }

    @GetMapping("/set-new-password")
    @Observed(
            name = "user.name",
            contextualName = "setNewPassword",
            lowCardinalityKeyValues = {"method", "setNewPassword"}
    )
    public void setNewPassword(@RequestParam String username, @RequestParam String password) {
        profileService.setNewPassword(username.trim(), password.trim());
    }
}
