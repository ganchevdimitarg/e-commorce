package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.dto.RegisterUserCommand;
import com.ganchevdimitarg.auth.dto.RegisterUserResponse;
import com.ganchevdimitarg.auth.dto.RequestPasswordResetCommand;
import com.ganchevdimitarg.auth.dto.SetNewPasswordCommand;
import com.ganchevdimitarg.auth.service.AccountService;
import com.ganchevdimitarg.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(cmd));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(final Authentication auth) {
        accountService.deleteOwnAccount(auth.getName()); // name == userId
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody final RequestPasswordResetCommand cmd) {
        passwordResetService.requestReset(cmd.email());
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/set-new-password")
    public ResponseEntity<Void> setNewPassword(
            @Valid @RequestBody final SetNewPasswordCommand cmd) {
        passwordResetService.confirmReset(cmd.token(), cmd.password());
        return ResponseEntity.noContent().build();
    }
}
