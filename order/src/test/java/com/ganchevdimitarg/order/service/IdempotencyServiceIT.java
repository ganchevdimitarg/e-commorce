package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.AbstractIntegrationTest;
import com.ganchevdimitarg.order.dto.OrderCreatedResponse;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void should_replayRecordValue_when_sameKeyReplayedWithRecordType() {
        String key = UUID.randomUUID().toString();
        AtomicInteger calls = new AtomicInteger();

        OrderCreatedResponse first = idempotencyService.execute(key, OrderCreatedResponse.class, () -> {
            calls.incrementAndGet();
            return new OrderCreatedResponse(4242L);
        });
        OrderCreatedResponse second = idempotencyService.execute(key, OrderCreatedResponse.class, () -> {
            calls.incrementAndGet();
            return new OrderCreatedResponse(9999L);
        });

        assertThat(first).isInstanceOf(OrderCreatedResponse.class);
        assertThat(first.orderNumber()).isEqualTo(4242L);
        assertThat(second).isInstanceOf(OrderCreatedResponse.class);
        assertThat(second.orderNumber()).isEqualTo(4242L);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void should_throwConflict_when_secondCallerCannotAcquireLock() throws Exception {
        String key = UUID.randomUUID().toString();
        CountDownLatch firstCallerHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseFirstCaller = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstCallFuture = executor.submit(() ->
                    idempotencyService.execute(key, String.class, () -> {
                        firstCallerHoldingLock.countDown();
                        try {
                            releaseFirstCaller.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "first-result";
                    }));

            assertThat(firstCallerHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Throwable> secondCallerFailure = new AtomicReference<>();
            Future<?> secondCallFuture = executor.submit(() -> {
                try {
                    idempotencyService.execute(key, String.class, () -> "second-result");
                } catch (Throwable t) {
                    secondCallerFailure.set(t);
                }
            });
            secondCallFuture.get(15, TimeUnit.SECONDS);

            releaseFirstCaller.countDown();
            assertThat(firstCallFuture.get(10, TimeUnit.SECONDS)).isEqualTo("first-result");

            assertThat(secondCallerFailure.get()).isInstanceOf(ConflictException.class);
        } finally {
            executor.shutdownNow();
        }
    }
}
