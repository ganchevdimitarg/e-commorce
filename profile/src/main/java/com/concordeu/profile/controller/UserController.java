package com.concordeu.profile.controller;

import com.concordeu.profile.annotation.ValidationInputRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final ProfileService profileService;

    @Operation(summary = "Get User By Username",  description = "Get user by username from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-by-username/{username}")
    public UserDto getUserByEmail(@PathVariable String username) {
        return profileService.getUserByUsername(username);
    }

    @Operation(summary = "Register User",  description = "Register user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register")
    @ValidationInputRequest
    public UserDto registerUser(@RequestBody UserRequestDto requestDto) {
        return profileService.createUser(requestDto);
    }

    @Operation(summary = "Update User",  description = "Update user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/update/{username}")
    @ValidationInputRequest
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @PathVariable String username) {
        profileService.updateUser(username, requestDto);
    }

    @Operation(summary = "Delete User",  description = "Delete user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete/{username}")
    public void deleteUser(@PathVariable String username) {
        profileService.deleteUser(username);
    }

}
