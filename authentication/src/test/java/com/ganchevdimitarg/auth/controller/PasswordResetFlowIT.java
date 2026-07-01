package com.ganchevdimitarg.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.OutboxEventRepository;
import com.ganchevdimitarg.auth.dao.PasswordResetTokenRepository;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.OutboxEvent;
import com.ganchevdimitarg.auth.domain.UserCredential;
import com.ganchevdimitarg.auth.event.PasswordResetRequestedEvent;
import com.ganchevdimitarg.auth.exception.ValidationException;
import com.ganchevdimitarg.auth.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PasswordResetFlowIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private UserCredentialRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private UUID seedUser(String email, String rawPassword) {
        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id);
        c.setEmail(email);
        c.setPasswordHash(passwordEncoder.encode(rawPassword));
        c.setRoles(Set.of("ROLE_USER"));
        c.setEnabled(true);
        userRepository.save(c);
        return id;
    }

    private String rawTokenFromOutbox(UUID userId) throws Exception {
        OutboxEvent row = outboxRepository.findAll().stream()
                .filter(e -> "auth.password.reset-requested".equals(e.getTopic()))
                .filter(e -> userId.toString().equals(e.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reset-requested outbox row for user " + userId));
        return objectMapper.readValue(row.getPayload(), PasswordResetRequestedEvent.class).rawToken();
    }

    @Test
    void should_resetPasswordAndConsumeToken_when_validTokenFlow() throws Exception {
        String email = "reset-flow@test.io";
        UUID userId = seedUser(email, "OldAa1@aaaa");
        String oldHash = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow().getPasswordHash();

        passwordResetService.requestReset(email);

        // A token row is persisted and an outbox event is queued for the notification service.
        assertThat(tokenRepository.findAll())
                .anyMatch(t -> t.getUserId().equals(userId) && t.getUsedAt() == null);
        String rawToken = rawTokenFromOutbox(userId);
        assertThat(rawToken).isNotBlank();

        passwordResetService.confirmReset(rawToken, "NewAa1@aaaa");

        UserCredential reloaded = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow();
        assertThat(passwordEncoder.matches("NewAa1@aaaa", reloaded.getPasswordHash())).isTrue();
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("OldAa1@aaaa", reloaded.getPasswordHash())).isFalse();

        // Single-use: replaying the same token fails.
        assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "NewAa1@aaaa"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired reset token");
    }

    @Test
    void should_accept202_when_requestResetForUnknownEmail() throws Exception {
        // No enumeration: an unknown email is answered identically to a known one.
        mvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-such-user@test.io\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void should_return400_when_setNewPasswordWithInvalidToken() throws Exception {
        // Account-takeover path is closed: without a valid token the reset is rejected (400),
        // never applied to any account.
        mvc.perform(patch("/api/v1/auth/set-new-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bogus-token\",\"password\":\"NewAa1@aaaa\"}"))
                .andExpect(status().isBadRequest());
    }
}
