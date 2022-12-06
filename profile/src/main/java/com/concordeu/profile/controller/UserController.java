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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private static final String ADMIN = "admin";
    private final ProfileService profileService;
    private final MailService mailService;

    @Operation(summary = "Get User By Username", description = "Get user by username from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-by-username")
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.read')")
    public UserDto getUserByUsername(Authentication authentication, @RequestParam String username) {
        String principalName = authentication.getName();

        if (!principalName.equals(username) && !principalName.equals(ADMIN)) {
            log.debug("User '{}' try to access another account '{}'", principalName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        return profileService.getUserByUsername(username);
    }

    @Operation(summary = "Register Admin", description = "Register admin in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-admin")
    @ValidationRequest
    public UserDto createAdmin(@RequestBody UserRequestDto requestDto) {
        UserDto user = profileService.createAdmin(requestDto);
        mailService.sendUserWelcomeMail(user.username());
        return user;
    }

    @Operation(summary = "Register Worker", description = "Register worker in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-worker")
    @ValidationRequest
    public UserDto createWorker(@RequestBody UserRequestDto requestDto) {
        UserDto user = profileService.createWorker(requestDto);
        mailService.sendUserWelcomeMail(user.username());
        return user;
    }

    @Operation(summary = "Register User", description = "Register user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-user")
    @ValidationRequest
    public UserDto createUser(@RequestBody UserRequestDto requestDto) {
        UserDto user = profileService.createUser(requestDto);
        mailService.sendUserWelcomeMail(user.username());
        return user;
    }

    @Operation(summary = "Update User", description = "Update user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/update-user")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public void updateUser(@RequestBody UserRequestDto requestDto,
                           @RequestParam String username) {
        profileService.updateUser(username, requestDto);
    }

    @Operation(summary = "Delete User", description = "Delete user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAuthority('SCOPE_profile.write')")
    public void deleteUser(@RequestParam String username) {
        profileService.deleteUser(username);
    }

}
