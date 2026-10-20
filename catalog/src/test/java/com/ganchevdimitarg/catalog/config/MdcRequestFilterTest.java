package com.ganchevdimitarg.catalog.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class MdcRequestFilterTest {

    @AfterEach
    void clearSecurityContextAndMdc() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static JwtAuthenticationToken jwtAuthentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_catalog.read")));
    }

    @Test
    void should_populateTraceSpanAndUserId_when_spanAndJwtAuthenticationExist() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("user-9"));

        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("attacker");
        HttpServletResponse response = mock(HttpServletResponse.class);

        String[] captured = new String[3];
        FilterChain chain = (req, res) -> {
            captured[0] = MDC.get("traceId");
            captured[1] = MDC.get("spanId");
            captured[2] = MDC.get("userId");
        };

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("trace-123");
        assertThat(captured[1]).isEqualTo("span-456");
        assertThat(captured[2]).isEqualTo("user-9");
        assertThat(MDC.get("traceId")).isNull();   // cleared on exit
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void should_setEmptyUserId_when_noAuthenticationIsPresent() throws Exception {
        SecurityContextHolder.clearContext();
        Tracer tracer = mock(Tracer.class);

        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("attacker");
        HttpServletResponse response = mock(HttpServletResponse.class);

        String[] captured = new String[1];
        FilterChain chain = (req, res) -> captured[0] = MDC.get("userId");

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEmpty();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void should_setEmptyUserId_when_authenticationIsAnonymous() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        Tracer tracer = mock(Tracer.class);

        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("attacker");
        HttpServletResponse response = mock(HttpServletResponse.class);

        String[] captured = new String[1];
        FilterChain chain = (req, res) -> captured[0] = MDC.get("userId");

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEmpty();
        assertThat(MDC.get("userId")).isNull();
    }
}
