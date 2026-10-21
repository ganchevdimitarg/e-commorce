package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Orchestrator probes must reach the health endpoint's group subpaths without a token.
 *
 * <p>The resource server permits the exact {@code /actuator/health} path; Spring
 * Security's {@code PathPatternRequestMatcher} does not match subpaths, so liveness and
 * readiness previously fell through to {@code anyRequest().authenticated()} and returned
 * 401. Anonymous callers get no details ({@code show-details: when-authorized}).
 */
@Tag("integration")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "management.endpoint.health.probes.enabled=true",
        // No Redis container backs this security test; its indicator would make the
        // aggregate DOWN and mask the status-code assertions.
        "management.health.redis.enabled=false"})
class HealthProbeSecurityIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void should_returnOkWithoutAuthentication_when_probeEndpointsCalled() throws Exception {
        for (String path : List.of("/actuator/health",
                "/actuator/health/liveness", "/actuator/health/readiness")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk());
        }
    }
}
