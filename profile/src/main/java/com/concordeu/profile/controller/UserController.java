package com.concordeu.profile.controller;

import com.concordeu.profile.annotation.ValidationRequest;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final ProfileService profileService;
    private final MailService mailService;

    @Operation(summary = "Get User By Username",  description = "Get user by username from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-by-username")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:read', 'SCOPE_worker:read', 'SCOPE_user:read')")
    public UserDto getUserByEmail(@RequestParam String username) {
        return profileService.getUserByUsername(username);
    }

    @Operation(summary = "Register User",  description = "Register user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-user")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:write', 'SCOPE_user:write')")
    public UserDto registerUser(@RequestBody UserRequestDto requestDto) {
        UserDto user = profileService.createUser(requestDto);
        mailService.sendUserWelcomeMail(user.username());
        return user;
    }

    @Operation(summary = "Update User",  description = "Update user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/update-user")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:write', 'SCOPE_user:write')")
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @RequestParam String username) {
        profileService.updateUser(username, requestDto);
    }

    @Operation(summary = "Delete User",  description = "Delete user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode="200", description ="Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:write', 'SCOPE_worker:write', 'SCOPE_user:write')")
    public void deleteUser(@RequestParam String username) {
        profileService.deleteUser(username);
    }

}
