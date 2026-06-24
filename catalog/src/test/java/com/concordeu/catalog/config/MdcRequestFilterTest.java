package com.concordeu.catalog.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class MdcRequestFilterTest {

    @Test
    void should_populateTraceAndSpanId_fromCurrentSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("user-9");
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
    }
}
