package com.ganchevdimitarg.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderPropagationInterceptorTest {

    private final GatewayHeaderPropagationInterceptor interceptor = new GatewayHeaderPropagationInterceptor();

    @Test
    void should_copyIdentityHeaders_when_presentOnInboundRequest() throws IOException {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader("X-User-Id", "user-1");
        inbound.addHeader("X-User-Roles", "ROLE_USER");
        inbound.addHeader("X-Gateway-Timestamp", "1000");
        inbound.addHeader("X-Gateway-Signature", "sig-abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        try {
            HttpRequest outbound = new MockClientHttpRequest();
            ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], 200);

            interceptor.intercept(outbound, new byte[0], execution);

            HttpHeaders headers = outbound.getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
            assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_USER");
            assertThat(headers.getFirst("X-Gateway-Timestamp")).isEqualTo("1000");
            assertThat(headers.getFirst("X-Gateway-Signature")).isEqualTo("sig-abc");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void should_notAddHeaders_when_noInboundRequestContext() throws IOException {
        RequestContextHolder.resetRequestAttributes();
        HttpRequest outbound = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], 200);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().getFirst("X-User-Id")).isNull();
    }
}
