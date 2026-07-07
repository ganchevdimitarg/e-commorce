package com.ganchevdimitarg.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ProblemAuthenticationEntryPoint} renders an RFC 9457
 * {@code application/problem+json} envelope with a 401 status when authentication fails.
 */
class ProblemAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ProblemAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new ProblemAuthenticationEntryPoint(objectMapper);
    }

    @Test
    void should_writeUnauthorizedProblemJson_when_authenticationFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("invalid token");

        entryPoint.commence(request, response, authException);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Authentication is required to access this resource");
        assertThat(body.get("properties").get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.get("properties").has("timestamp")).isTrue();
    }
}
