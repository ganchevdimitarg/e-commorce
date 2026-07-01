package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerRegisterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserCredentialRepository repository;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void should_create_credentialAndEmitEvent_when_registerValid() throws Exception {
        // Subscribe (earliest) before the request; the transactional-outbox relay publishes the
        // event asynchronously, so we poll until it arrives and filter by the unique email.
        consumer = newConsumer("auth.user.registered");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"new@test.io","password":"Aa1@aaaa","role":"USER",
                           "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
                           "city":"Sofia","street":"Main","postCode":"1000"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        assertThat(repository.findByEmailAndDeletedAtIsNull("new@test.io")).isPresent();

        List<String> matching = new ArrayList<>();
        await().atMost(20, SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.records("auth.user.registered").forEach(r -> {
                if (r.value() != null && r.value().contains("new@test.io")) {
                    matching.add(r.value());
                }
            });
            assertThat(matching).isNotEmpty();
        });
    }

    @Test
    void should_returnConflict_when_emailAlreadyRegistered() throws Exception {
        String body = """
              {"email":"dupe@test.io","password":"Aa1@aaaa","role":"USER",
               "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
               "city":"Sofia","street":"Main","postCode":"1000"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
