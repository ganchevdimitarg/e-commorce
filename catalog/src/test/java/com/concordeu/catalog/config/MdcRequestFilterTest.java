package com.concordeu.catalog.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MdcRequestFilterTest {

    @Test
    void should_setAndClearMdc_aroundChain() throws Exception {
        MdcRequestFilter filter = new MdcRequestFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> userInsideChain = new AtomicReference<>();
        AtomicReference<String> serviceInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            userInsideChain.set(MDC.get("userId"));
            serviceInsideChain.set(MDC.get("serviceId"));
        };

        filter.doFilter(request, response, chain);

        assertThat(userInsideChain.get()).isEqualTo("user-42");
        assertThat(serviceInsideChain.get()).isEqualTo("catalog-service");
        // cleared after the chain
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("serviceId")).isNull();
    }
}
