package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.dto.RegisterUserCommand;
import com.ganchevdimitarg.auth.dto.RegisterUserResponse;
import com.ganchevdimitarg.auth.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(cmd));
    }
}
