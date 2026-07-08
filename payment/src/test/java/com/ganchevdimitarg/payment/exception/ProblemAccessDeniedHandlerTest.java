package com.ganchevdimitarg.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ProblemAccessDeniedHandler} renders an RFC 9457
 * {@code application/problem+json} envelope with a 403 status when access is denied.
 */
class ProblemAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ProblemAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProblemAccessDeniedHandler(objectMapper);
    }

    @Test
    void should_writeForbiddenProblemJson_when_accessIsDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException accessDeniedException = new AccessDeniedException("not allowed");

        handler.handle(request, response, accessDeniedException);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asText()).isEqualTo("You do not have permission to access this resource");
        assertThat(body.get("properties").get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("properties").has("timestamp")).isTrue();
    }
}
