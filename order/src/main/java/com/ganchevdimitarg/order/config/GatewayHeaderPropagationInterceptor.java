package com.ganchevdimitarg.order.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Forwards the gateway-signed identity headers from the current inbound request onto
 * every outbound call order makes. The signature authenticates the original
 * gateway-issued claim, not order's own identity, so it must be copied unchanged
 * rather than regenerated — this is what lets payment trust order's delegated charge
 * requests without granting order (or anyone else) a blanket ability to spoof
 * X-User-Id.
 */
public class GatewayHeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    private static final String[] FORWARDED_HEADERS = {
            "X-User-Id", "X-User-Roles", "X-Gateway-Timestamp", "X-Gateway-Signature"
    };

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        HttpServletRequest inbound = currentInboundRequest();
        if (inbound != null) {
            for (String header : FORWARDED_HEADERS) {
                String value = inbound.getHeader(header);
                if (value != null) {
                    request.getHeaders().set(header, value);
                }
            }
        }
        return execution.execute(request, body);
    }

    private HttpServletRequest currentInboundRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
