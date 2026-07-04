package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.AbstractIntegrationTest;
import com.ganchevdimitarg.order.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceIT extends AbstractIntegrationTest {

    @Autowired
    IdempotencyService idempotencyService;

    @Test
    void should_runActionOnce_when_sameKeyReplayed() {
        String key = UUID.randomUUID().toString();
        AtomicInteger calls = new AtomicInteger();

        String first = idempotencyService.execute(key, String.class, () -> {
            calls.incrementAndGet();
            return "result-" + key;
        });
        String second = idempotencyService.execute(key, String.class, () -> {
            calls.incrementAndGet();
            return "SHOULD-NOT-RUN";
        });

        assertThat(first).isEqualTo("result-" + key);
        assertThat(second).isEqualTo(first);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void should_reject_when_keyBlank() {
        assertThatThrownBy(() -> idempotencyService.execute("  ", String.class, () -> "x"))
                .isInstanceOf(ValidationException.class);
    }
}
