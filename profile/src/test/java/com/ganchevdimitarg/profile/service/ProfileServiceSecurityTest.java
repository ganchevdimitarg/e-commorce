package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileServiceSecurityTest {

    @Test
    void should_requireProfileWriteScope_when_updatingProfile() throws NoSuchMethodException {
        var method = ProfileService.class.getMethod("updateProfile", String.class, UpdateProfileCommand.class);
        var preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')");
    }

    @Test
    void should_requireProfileWriteScope_when_settingUpPayment() throws NoSuchMethodException {
        var method = ProfileService.class.getMethod("setupPayment", String.class, CardSetupCommand.class);
        var preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')");
    }
}
