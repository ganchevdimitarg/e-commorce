package com.ganchevdimitarg.auth.config.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MdcRequestFilterTest {

    @Test
    void should_clearMdc_when_requestCompletes() throws Exception {
        Tracer tracer = mock(Tracer.class);
        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-123");
        FilterChain chain = (req, res) ->
                assertThat(MDC.get("serviceId")).isEqualTo("auth-service");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(MDC.get("serviceId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }
}
