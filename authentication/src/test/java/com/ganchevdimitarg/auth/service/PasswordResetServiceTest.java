package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.PasswordResetTokenRepository;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.PasswordResetToken;
import com.ganchevdimitarg.auth.domain.UserCredential;
import com.ganchevdimitarg.auth.event.PasswordResetRequestedEvent;
import com.ganchevdimitarg.auth.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private PasswordResetService service;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;
    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void should_persistTokenAndWriteOutboxEvent_when_requestResetForKnownEmail() {
        UUID userId = UUID.randomUUID();
        UserCredential user = new UserCredential();
        user.setId(userId);
        user.setEmail("known@test.io");
        when(userCredentialRepository.findByEmailAndDeletedAtIsNull("known@test.io"))
                .thenReturn(Optional.of(user));

        service.requestReset("known@test.io");

        verify(tokenRepository).save(tokenCaptor.capture());
        verify(outboxWriter).write(
                eq("auth.password.reset-requested"), eq(userId.toString()),
                eventCaptor.capture(), eq("user"), eq(userId.toString()));

        PasswordResetToken saved = tokenCaptor.getValue();
        PasswordResetRequestedEvent event = (PasswordResetRequestedEvent) eventCaptor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        // The raw token is only ever carried in the event; the stored hash must NOT be the raw token.
        assertThat(event.rawToken()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(event.rawToken());
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(event.rawToken()));
        assertThat(event.userId()).isEqualTo(userId.toString());
        assertThat(event.email()).isEqualTo("known@test.io");
    }

    @Test
    void should_beNoOp_when_requestResetForUnknownEmail() {
        when(userCredentialRepository.findByEmailAndDeletedAtIsNull("nobody@test.io"))
                .thenReturn(Optional.empty());

        service.requestReset("nobody@test.io");

        verify(tokenRepository, never()).save(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void should_reEncodePasswordAndMarkTokenUsed_when_confirmWithValidToken() {
        String rawToken = "raw-token-value";
        UUID userId = UUID.randomUUID();

        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndDeletedAtIsNull(sha256Hex(rawToken)))
                .thenReturn(Optional.of(token));

        UserCredential user = new UserCredential();
        user.setId(userId);
        user.setPasswordHash("OLD_HASH");
        when(userCredentialRepository.findByIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewAa1@aaaa")).thenReturn("NEW_HASH");

        service.confirmReset(rawToken, "NewAa1@aaaa");

        assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userCredentialRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void should_throwValidationException_when_confirmWithUnknownToken() {
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("bogus", "NewAa1@aaaa"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired reset token");

        verify(userCredentialRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void should_throwValidationException_when_confirmWithExpiredToken() {
        String rawToken = "expired-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(UUID.randomUUID());
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndDeletedAtIsNull(sha256Hex(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmReset(rawToken, "NewAa1@aaaa"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid or expired reset token");

        verify(userCredentialRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }
}
