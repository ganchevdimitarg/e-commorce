package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Customer-profile endpoints keyed by the JWT subject ({@code userId}).
 *
 * <p>Registration and account deletion are owned by the auth service and
 * arrive here as Kafka events — they are not exposed as HTTP endpoints.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Returns the authenticated user's own profile. Identity is the JWT
     * subject — no path or query identifier is accepted.
     *
     * @param authentication the current authentication; {@code getName()} is the userId
     * @return a {@link Mono} emitting 200 with the {@link UserDto}
     * @throws InvalidRequestDataException if no active profile exists
     */
    @Operation(summary = "Get My Profile", description = "Get the authenticated user's profile",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/me")
    public Mono<ResponseEntity<UserDto>> getMe(Authentication authentication) {
        return profileService.getByUserId(authentication.getName())
                .map(ResponseEntity::ok);
    }

    /**
     * Updates the authenticated user's own profile display fields.
     *
     * @param authentication the current authentication; {@code getName()} is the userId
     * @param command        the new display values
     * @return a {@link Mono} emitting 204 No Content
     * @throws InvalidRequestDataException if no active profile exists
     */
    @Operation(summary = "Update My Profile", description = "Update the authenticated user's profile",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/me")
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public Mono<ResponseEntity<Void>> updateMe(Authentication authentication,
                                               @Valid @RequestBody UpdateProfileCommand command) {
        return profileService.updateProfile(authentication.getName(), command)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /**
     * Sets up a payment customer and attaches a card for the authenticated user.
     *
     * @param authentication the current authentication; {@code getName()} is the userId
     * @param command        the card details
     * @return a {@link Mono} emitting 201 with the {@link UserDto} carrying the card ID
     * @throws InvalidRequestDataException if no active profile exists or payment is down
     */
    @Operation(summary = "Set Up Payment", description = "Create a payment customer and attach a card",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/payment-setup")
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    public Mono<ResponseEntity<UserDto>> setupPayment(Authentication authentication,
                                                      @Valid @RequestBody CardSetupCommand command) {
        return profileService.setupPayment(authentication.getName(), command)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user));
    }
}
