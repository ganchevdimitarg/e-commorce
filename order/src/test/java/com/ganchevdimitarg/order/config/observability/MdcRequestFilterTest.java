package com.ganchevdimitarg.order.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MdcRequestFilterTest {

    @Test
    void should_populateThenClearMdc_around_theChain() throws Exception {
        MdcRequestFilter filter = new MdcRequestFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-User-Id")).thenReturn("john");
        when(req.getHeader("traceparent")).thenReturn("00-abc-def-01");

        String[] insideUser = new String[1];
        String[] insideService = new String[1];
        doAnswer(inv -> {
            insideUser[0] = MDC.get("userId");
            insideService[0] = MDC.get("serviceId");
            return null;
        }).when(chain).doFilter(req, res);

        filter.doFilter(req, res, chain);

        assertThat(insideUser[0]).isEqualTo("john");
        assertThat(insideService[0]).isEqualTo("order-service");
        assertThat(MDC.get("userId")).isNull(); // cleared afterwards
    }
}
