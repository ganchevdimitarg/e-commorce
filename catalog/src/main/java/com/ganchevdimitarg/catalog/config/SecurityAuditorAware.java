package com.ganchevdimitarg.catalog.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the JPA audit columns' author from the validated JWT principal.
 *
 * <p>Scheduled work and anonymous requests have no principal, so they are recorded as
 * {@code system} rather than left blank.
 */
@Component("auditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM_AUDITOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM_AUDITOR);
        }
        return Optional.of(authentication.getName());
    }
}
