package com.concordeu.profile.controller;

import com.concordeu.profile.annotation.ValidationInputRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.service.auth.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final ProfileService profileService;

    @GetMapping("/get-by-email/{email}")
    public UserDto getUserByEmail(@PathVariable String email) {
        return profileService.getUserByEmail(email);
    }

    @PostMapping("/register")
    @ValidationInputRequest
    public UserDto registerUser(@RequestBody UserRequestDto requestDto) {
        return profileService.createUser(requestDto);
    }

    @PutMapping("/update/{email}")
    @ValidationInputRequest
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @PathVariable String email) {
        profileService.updateUser(email, requestDto);
    }

    @DeleteMapping("/delete/{email}")
    public void deleteUser(@PathVariable String email) {
        profileService.deleteUser(email);
    }

}
