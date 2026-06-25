package com.concordeu.catalog.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
class MeasureAspectTest {

    private MeasureAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new MeasureAspect();
    }

    @Test
    void should_proceedAndReturnResult_when_methodSucceeds() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint("successMethod");
        when(pjp.proceed()).thenReturn("expectedResult");

        Object result = aspect.measureExecutionTime(pjp);

        assertThat(result).isEqualTo("expectedResult");
        verify(pjp).proceed();
    }

    @Test
    void should_propagateException_when_methodThrows() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint("failingMethod");
        RuntimeException expected = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(expected);

        assertThatThrownBy(() -> aspect.measureExecutionTime(pjp))
                .isSameAs(expected);
        verify(pjp).proceed();
    }

    @Test
    void should_notThrow_when_invokedConcurrently() throws Exception {
        int threadCount = 16;
        int iterationsPerThread = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            ProceedingJoinPoint pjp = mockJoinPoint("concurrentMethod");
                            when(pjp.proceed()).thenReturn("ok");
                            aspect.measureExecutionTime(pjp);
                        }
                    } catch (Throwable ex) {
                        throw new AssertionError("Concurrent invocation failed", ex);
                    }
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }
    }

    private static ProceedingJoinPoint mockJoinPoint(String methodName) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        return pjp;
    }
}
