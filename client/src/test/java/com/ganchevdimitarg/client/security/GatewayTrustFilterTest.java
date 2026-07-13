package com.ganchevdimitarg.client.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GatewayTrustFilterTest {

    private static final String SECRET = "test-shared-secret";

    private GatewaySignatureVerifier verifier;
    private GatewayTrustFilter filter;

    @BeforeEach
    void setUp() {
        verifier = new GatewaySignatureVerifier(SECRET);
        filter = new GatewayTrustFilter(verifier);
    }

    @Test
    void should_passThrough_when_noUserIdHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payment/customer/get-customer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_allowRequest_when_signatureValidAndFresh() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Roles", "ROLE_USER");
        request.addHeader("X-Gateway-Timestamp", timestamp);
        request.addHeader("X-Gateway-Signature", signature);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_reject401_when_userIdPresentButSignatureMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "attacker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        verifyNoInteractions(chain);
    }

    @Test
    void should_reject401_when_signatureInvalid() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "attacker");
        request.addHeader("X-Gateway-Timestamp", timestamp);
        request.addHeader("X-Gateway-Signature", "forged-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
