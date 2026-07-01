package com.ganchevdimitarg.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.auth.dao.OutboxEventRepository;
import com.ganchevdimitarg.auth.domain.OutboxEvent;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import com.ganchevdimitarg.auth.exception.OutboxSerializationException;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private Tracer tracer;

    @Captor
    private ArgumentCaptor<OutboxEvent> captor;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OutboxWriter writer() {
        return new OutboxWriter(repository, objectMapper, tracer);
    }

    @Test
    void should_savePendingOutboxRow_when_writeCalled() {
        String userId = UUID.randomUUID().toString();
        UserRegisteredEvent event = new UserRegisteredEvent(
                userId, "e@test.io", Set.of("ROLE_USER"),
                "Anna", "Smith", "0888123456", "Sofia", "Main", "1000", Instant.now());

        writer().write("auth.user.registered", userId, event, "user", userId);

        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTopic()).isEqualTo("auth.user.registered");
        assertThat(saved.getMessageKey()).isEqualTo(userId);
        assertThat(saved.getAggregateType()).isEqualTo("user");
        assertThat(saved.getAggregateId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPayload()).isNotBlank().contains("e@test.io");
        assertThat(saved.getTraceId()).isNotNull();
        assertThat(saved.getCorrelationId()).isNotNull();
    }

    @Test
    void should_throwOutboxSerializationException_when_eventNotSerialisable() {
        Object unserialisable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() ->
                writer().write("auth.user.registered", "k", unserialisable, "user", "k"))
                .isInstanceOf(OutboxSerializationException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
