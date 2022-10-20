package com.concordeu.auth.controller;

import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;
import com.concordeu.auth.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthUserDto registerUser(@RequestBody AuthUserRequestDto requestDto) {
        return authService.createUser(requestDto);
    }

    @PutMapping("/update/{email}")
    public void updateUser(@PathVariable String email, AuthUserRequestDto requestDto) {
        authService.updateUser(email, requestDto);
    }

    @DeleteMapping("/delete/{email}")
    public void deleteUser(@PathVariable String email) {
        authService.deleteUser(email);
    }


}
