package com.concordeu.auth.controller;

import com.concordeu.auth.annotation.ValidationInputRequest;
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

    @GetMapping("/get-by-email/{email}")
    public AuthUserDto getUserByEmail(@PathVariable String email) {
        return authService.getUserByEmail(email);
    }

    @PostMapping("/register")
    @ValidationInputRequest
    public AuthUserDto registerUser(@RequestBody AuthUserRequestDto requestDto) {
        return authService.createUser(requestDto);
    }

    @PutMapping("/update/{email}")
    @ValidationInputRequest
    public void updateUser(@RequestBody AuthUserRequestDto requestDto, @PathVariable String email) {
        authService.updateUser(email, requestDto);
    }

    @DeleteMapping("/delete/{email}")
    public void deleteUser(@PathVariable String email) {
        authService.deleteUser(email);
    }

}
