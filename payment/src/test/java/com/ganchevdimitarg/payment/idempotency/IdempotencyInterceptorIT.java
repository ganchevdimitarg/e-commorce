package com.ganchevdimitarg.payment.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context IT driving real HTTP through {@link IdempotencyInterceptor} against
 * Testcontainers Redis. The interceptor runs in {@code preHandle}, before the
 * controller/service/Stripe gateway, so these tests never make a real Stripe call and
 * never need a seeded customer: a request that clears the interceptor still 404s in
 * {@code ChargeServiceImpl#createCharge} on the customer lookup, before the gateway is
 * ever invoked. {@link PaymentGateway} is mocked as belt-and-suspenders so no accidental
 * network call can occur regardless.
 */
@AutoConfigureMockMvc
class IdempotencyInterceptorIT extends AbstractIntegrationTest {

    private static final String CREATE_CHARGE_URL = "/api/v1/payment/charge/create-charge";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Test
    void should_proceedPastInterceptor_when_writeHasNoIdempotencyKey() throws Exception {
        // Optional-but-honored: no Idempotency-Key means the interceptor does NOT reject the
        // request. It clears preHandle and reaches ChargeServiceImpl#createCharge, which 404s
        // on the unseeded customer lookup — proving the interceptor neither 400'd nor 409'd.
        mockMvc.perform(post(CREATE_CHARGE_URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChargeCommand())))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return409_when_idempotencyKeyReplayed() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(validChargeCommand());

        // First call clears the interceptor and stores the key; downstream 404s on the
        // unseeded customer lookup, well before any gateway call — its status is not asserted.
        mockMvc.perform(post(CREATE_CHARGE_URL)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_KEY_HEADER, key)
                .content(body));

        mockMvc.perform(post(CREATE_CHARGE_URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void should_storeKeyInRedis_when_firstWrite() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post(CREATE_CHARGE_URL)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_KEY_HEADER, key)
                .content(objectMapper.writeValueAsString(validChargeCommand())));

        assertThat(redis.hasKey("payment:idempotency:" + key)).isTrue();
    }

    private static CreateChargeCommand validChargeCommand() {
        return new CreateChargeCommand("nonexistent@doe.com", "cus_1", "card_1", 500L, "usd", "nonexistent@doe.com");
    }
}
