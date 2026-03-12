package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.dto.SetNewPasswordRequestDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.MailService;
import com.ganchevdimitarg.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;
    private final MailService mailService;

    /**
     * Creates a new admin user profile with full system permissions
     * and sends a welcome email notification via Kafka.
     *
     * @param requestDto the request payload containing admin profile details
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 201 and the created {@link UserDto}
     * @throws InvalidRequestDataException if a profile with the same username already exists
     */
    @Operation(summary = "Register Admin", description = "Register admin in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-admin")
    public Mono<ResponseEntity<UserDto>> createAdmin(@Valid @RequestBody UserRequestDto requestDto) {
        return profileService.createAdmin(requestDto)
                .flatMap(user -> mailService.sendUserWelcomeMail(user.username())
                        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(user)));
    }

    /**
     * Creates a new worker user profile with read-only permissions
     * and sends a welcome email notification via Kafka.
     *
     * @param requestDto the request payload containing worker profile details
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 201 and the created {@link UserDto}
     * @throws InvalidRequestDataException if a profile with the same username already exists
     */
    @Operation(summary = "Register Worker", description = "Register worker in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-worker")
    public Mono<ResponseEntity<UserDto>> createWorker(@Valid @RequestBody UserRequestDto requestDto) {
        return profileService.createWorker(requestDto)
                .flatMap(user -> mailService.sendUserWelcomeMail(user.username())
                        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(user)));
    }

    /**
     * Creates a new regular user profile, sets up their payment customer
     * and card, and sends a welcome email notification via Kafka.
     *
     * @param requestDto the request payload containing user profile and card details
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 201 and the created {@link UserDto}
     * @throws InvalidRequestDataException if a profile already exists or payment service is unavailable
     */
    @Operation(summary = "Register User", description = "Register user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/register-user")
    public Mono<ResponseEntity<UserDto>> createUser(@Valid @RequestBody UserRequestDto requestDto) {
        return profileService.createUser(requestDto)
                .flatMap(user -> mailService.sendUserWelcomeMail(user.username())
                        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(user)));
    }

    /**
     * Retrieves a user profile by username. Access is restricted to the profile
     * owner, admins, and gateway clients via {@code @PreAuthorize}.
     *
     * @param username the username query parameter identifying the profile
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 200 and the {@link UserDto}
     * @throws UsernameNotFoundException if no profile exists for the given username
     */
    @Operation(summary = "Get Profile By Username", description = "Get user by username from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-by-username")
    @PreAuthorize("authentication.name == #username or hasAnyRole('ADMIN', 'GATEWAY')")
    public Mono<ResponseEntity<UserDto>> getUserByUsername(@RequestParam String username) {
        return profileService.getUserByUsername(username.trim())
                .map(ResponseEntity::ok);
    }

    /**
     * Updates an existing user profile with the provided request payload.
     * Requires {@code SCOPE_profile.write} or {@code ROLE_USER} authority.
     *
     * @param requestDto the request payload containing updated profile fields
     * @param username   the username query parameter identifying the profile to update
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 204 No Content
     * @throws UsernameNotFoundException if no profile exists for the given username
     */
    @Operation(summary = "Update Profile", description = "Update user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/update-user")
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public Mono<ResponseEntity<Void>> updateUser(@Valid @RequestBody UserRequestDto requestDto,
                                                 @RequestParam String username) {
        return profileService.updateUser(username.trim(), requestDto)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
    /**
     * Deletes the authenticated user's profile and their associated
     * payment customer record. Requires {@code SCOPE_profile.write} authority.
     *
     * @param authentication the current authentication used to resolve the username
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 204 No Content
     * @throws UsernameNotFoundException   if no profile exists for the authenticated user
     * @throws InvalidRequestDataException if the payment service is unavailable
     */
    @Operation(summary = "Delete Profile", description = "Delete user in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-user")
    @PreAuthorize("hasAuthority('SCOPE_profile.write')")
    public Mono<ResponseEntity<Void>> deleteUser(Authentication authentication) {
        return profileService.deleteUser(authentication.getName().trim())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /**
     * Initiates a password reset flow for the given username.
     * Generates a JWT token and delivers it exclusively via email — never in the response.
     *
     * @param username the username of the user requesting a password reset
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 204 No Content
     * @throws InvalidRequestDataException if no profile exists for the given username
     */
    @Operation(summary = "Password Reset", description = "Generates a reset token and sends it to the user via email",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "User Not Found"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/password-reset")
    public Mono<ResponseEntity<Void>> passwordReset(@RequestParam String username) {
        return profileService.passwordReset(username.trim())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /**
     * Validates whether the provided password reset token is still valid.
     *
     * @param token the JWT password reset token to validate
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 200 and a boolean result
     */
    @Operation(summary = "Validate Password Reset Token", description = "Checks if the password reset token is valid",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "Invalid Token"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/password-reset-token")
    public Mono<ResponseEntity<Boolean>> isValidPasswordReset(@RequestParam String token) {
        return profileService.isPasswordResetTokenValid(token.trim())
                .map(ResponseEntity::ok);
    }

    /**
     * Sets a new password for the specified user after validating the request body.
     *
     * @param request the request body containing the username and new password
     * @return a {@link Mono} emitting {@link ResponseEntity} with status 204 No Content
     * @throws InvalidRequestDataException if no profile exists for the given username
     */
    @Operation(summary = "Set New Password", description = "Sets a new password for the user",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PatchMapping("/set-new-password")
    public Mono<ResponseEntity<Void>> setNewPassword(@Valid @RequestBody SetNewPasswordRequestDto request) {
        return profileService.setNewPassword(request.username().trim(), request.password().trim())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}