package com.ganchevdimitarg.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserTest {

    @Test
    void should_excludePassword_when_toStringCalled() {
        AuthUser user = AuthUser.builder()
                .username("user@test.io")
                .password("S3cr3t@1")
                .build();

        assertThat(user.toString()).doesNotContain("S3cr3t@1");
        assertThat(user.getPassword()).isEqualTo("S3cr3t@1");
    }
}
