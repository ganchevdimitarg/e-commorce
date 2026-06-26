package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.event.UserRegisteredEvent;
import reactor.core.publisher.Mono;

public interface ProfileService {

    /**
     * Creates a profile shell from a consumed {@code UserRegisteredEvent}.
     * Idempotent on {@code userId} — a second delivery is a no-op.
     */
    Mono<Void> createProfileShell(UserRegisteredEvent event);

    /**
     * Soft-deletes the profile for the given userId and tears down the
     * associated payment customer. Sets {@code deletedAt}; never hard-deletes.
     */
    Mono<Void> softDeleteProfile(String userId);

    /**
     * Returns the active profile for the given userId, enriched with the
     * payment card reference fetched from the payment service.
     */
    Mono<UserDto> getByUserId(String userId);

    /**
     * Updates the display fields of the active profile for the given userId.
     */
    Mono<Void> updateProfile(String userId, UpdateProfileCommand command);

    /**
     * Sets up a payment customer and attaches a card for the given userId.
     */
    Mono<UserDto> setupPayment(String userId, CardSetupCommand command);
}
