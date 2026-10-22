package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The management endpoints are split from the public app port.
 *
 * <p>The app port serves only the API, Swagger and the health probes it permits; aggregate
 * health, metrics and info live on the separate management port. That port is intentionally
 * unauthenticated for monitoring scrapers, so it must not be published outside the
 * monitoring network.
 */
@Tag("integration")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.endpoint.health.probes.enabled=true",
        // No Redis container backs this security test; its indicator would make the
        // aggregate DOWN and mask the status-code assertions.
        "management.health.redis.enabled=false"})
class HealthProbeSecurityIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @LocalManagementPort
    int managementPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void should_notExposeActuatorEndpointsOnTheAppPort_when_managementPortIsSeparate() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void should_serveHealthAndMetricsOnTheManagementPort_when_calledWithoutAuthentication() {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness", "/actuator/prometheus", "/actuator/info")) {
            var response = restTemplate.getForEntity(
                    "http://localhost:" + managementPort + path, String.class);
            assertThat(response.getStatusCode())
                    .as("%s responded with %s", path, response.getBody())
                    .isEqualTo(HttpStatus.OK);
        }
    }
}
