package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerDeleteIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserCredentialRepository repository;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void should_softDeleteAndEmitEvent_when_deleteOwnAccount() throws Exception {
        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id);
        c.setEmail("del@test.io");
        c.setPasswordHash("{noop}pw");
        c.setRoles(Set.of("ROLE_USER"));
        c.setEnabled(true);
        repository.save(c);

        // Subscribe before the action so we do not miss the event.
        // auth.user.deleted is a fresh topic in each test run (no prior records),
        // so earliest offset reset guarantees we see the single record we publish.
        consumer = newConsumer("auth.user.deleted");

        mvc.perform(delete("/api/v1/auth/account").with(user(id.toString()).roles("USER")))
                .andExpect(status().isNoContent());

        assertThat(repository.findByIdAndDeletedAtIsNull(id)).isEmpty();         // soft-deleted
        assertThat(repository.findById(id)).get()                                // row still exists
                .satisfies(row -> {
                    assertThat(row.getDeletedAt()).isNotNull();
                    assertThat(row.isEnabled()).isFalse();
                });
        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.records("auth.user.deleted"))
                .anyMatch(r -> r.value().contains(id.toString()));
    }
}
