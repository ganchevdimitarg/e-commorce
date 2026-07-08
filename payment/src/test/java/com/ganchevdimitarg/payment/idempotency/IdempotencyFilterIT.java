package com.ganchevdimitarg.payment.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.exception.PaymentGatewayException;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context IT for {@link IdempotencyFilter} against Testcontainers Redis + Postgres,
 * proving cache-aside replay semantics: a repeated key replays the stored response (the
 * provider is hit once), a transient failure never poisons the key, and a concurrent
 * in-flight duplicate is rejected with 409. {@link PaymentGateway} is mocked so no real
 * Stripe call occurs.
 */
@AutoConfigureMockMvc
class IdempotencyFilterIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/payment/charge/create-charge";
    private static final String KEY_HEADER = "Idempotency-Key";
    private static final String KEY_PREFIX = "payment:idempotency:";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerDao customerDao;
    @MockitoBean
    private PaymentGateway paymentGateway;

    private String seedCustomer() {
        String username = "user-" + UUID.randomUUID() + "@doe.com";
        customerDao.save(AppCustomer.builder()
                .customerId("cus_" + UUID.randomUUID())
                .username(username)
                .customerName(username)
                .build());
        return username;
    }

    private GatewayCharge someCharge() {
        return new GatewayCharge("ch_" + UUID.randomUUID(), 500L, "usd", "cus", "john@doe.com", "succeeded");
    }

    private MockHttpServletRequestBuilder charge(String user, String key) throws Exception {
        return post(URL)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                .header("X-User-Id", user)
                .header(KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateChargeCommand("card_1", 500L, "usd", "john@doe.com")));
    }

    @Test
    void should_replayStoredResponse_when_sameKeyRepeated() throws Exception {
        String user = seedCustomer();
        String key = UUID.randomUUID().toString();
        when(paymentGateway.createCharge(any(), eq(key))).thenReturn(someCharge());

        String first = mockMvc.perform(charge(user, key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(charge(user, key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        // The provider was invoked exactly once; the duplicate replayed the stored response.
        verify(paymentGateway, times(1)).createCharge(any(), eq(key));
        assertThat(redis.hasKey(KEY_PREFIX + key)).isTrue();
    }

    @Test
    void should_notPoisonKey_when_firstAttemptFails() throws Exception {
        String user = seedCustomer();
        String key = UUID.randomUUID().toString();
        when(paymentGateway.createCharge(any(), eq(key)))
                .thenThrow(new PaymentGatewayException("provider down"));

        mockMvc.perform(charge(user, key)).andExpect(status().isBadGateway());
        // A retry with the same key after a transient failure must be processed again, never 409'd.
        mockMvc.perform(charge(user, key)).andExpect(status().isBadGateway());

        verify(paymentGateway, times(2)).createCharge(any(), eq(key));
    }

    @Test
    void should_return409_when_concurrentRequestHoldsLock() throws Exception {
        String user = seedCustomer();
        String key = UUID.randomUUID().toString();
        // Simulate an in-flight duplicate: lock held, no stored response yet.
        redis.opsForValue().set(KEY_PREFIX + key + ":lock", "1", Duration.ofSeconds(30));

        mockMvc.perform(charge(user, key)).andExpect(status().isConflict());
    }

    @Test
    void should_proceed_when_noIdempotencyKey() throws Exception {
        String user = seedCustomer();
        when(paymentGateway.createCharge(any(), any())).thenReturn(someCharge());

        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateChargeCommand("card_1", 500L, "usd", "john@doe.com"))))
                .andExpect(status().isOk());
    }
}
