package com.ganchevdimitarg.notification.controller;

import com.ganchevdimitarg.notification.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/notification/send-email";
    private static final String VALID_BODY = """
            {"recipient":"mvc@test.com","subject":"Hi","msgBody":"A valid body over ten chars"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return200AndSendEmail_when_authorized() throws Exception {
        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_notification.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.recipient").value("mvc@test.com"));

        assertThat(GREEN_MAIL.getReceivedMessages()).isNotEmpty();
    }

    @Test
    void should_return403_when_scopeMissing() throws Exception {
        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_something.else")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400ProblemJson_when_bodyInvalid() throws Exception {
        String invalid = """
                {"recipient":"not-an-email","subject":"Hi","msgBody":"short"}
                """;
        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_notification.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
