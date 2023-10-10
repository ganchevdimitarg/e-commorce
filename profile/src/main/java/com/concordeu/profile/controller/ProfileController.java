package com.concordeu.profile.controller;

import com.concordeu.profile.annotation.ValidationRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register-admin")
    @ValidationRequest
    public ResponseEntity<Mono<UserDto>> createAdmin(@RequestBody UserRequestDto requestDto) {
        return ResponseEntity.ok(profileService.createAdmin(requestDto));
    }

    @PostMapping("/register-worker")
    @ValidationRequest
    public ResponseEntity<Mono<UserDto>> createWorker(@RequestBody UserRequestDto requestDto) {
        return ResponseEntity.ok(profileService.createWorker(requestDto));
    }

    @PostMapping("/register-user")
    @ValidationRequest
    public ResponseEntity<Mono<UserDto>> createUser(@RequestBody UserRequestDto requestDto) {
        return ResponseEntity.ok(profileService.createUser(requestDto));
    }

    @GetMapping("/get-by-username")
    public ResponseEntity<Mono<UserDto>> getUserByUsername(Authentication authentication, @RequestParam String username) throws ExecutionException, InterruptedException, TimeoutException {
        String principalName = authentication.getName();

        if (principalName.equals(username.trim()) ||
                principalName.equals(ADMIN) ||
                principalName.equals("gateway")) {
            return ResponseEntity.ok(profileService.getUserByUsername(username.trim()));
        }

        log.debug("Profile '{}' try to access another account '{}'", principalName, username);
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    @PutMapping("/update-user")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @RequestParam String username) {
        profileService.updateUser(username.trim(), requestDto);
    }

    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAuthority('SCOPE_profile.write')")
    public void deleteUser(Authentication authentication) {
        profileService.deleteUser(authentication.getName().trim());
    }

    @GetMapping("/password-reset")
    public String passwordReset(@RequestParam String username) {
        return String.format("""
                token: %s
                """, profileService.passwordReset(username.trim()).subscribe());
    }

    @GetMapping("/password-reset-token")
    public boolean isValidPasswordReset(@RequestParam String token) {
        return profileService.isPasswordResetTokenValid(token.trim());
    }

    @GetMapping("/set-new-password")
    public void setNewPassword(@RequestParam String username, @RequestParam String password) {
        profileService.setNewPassword(username.trim(), password.trim());
    }
}
