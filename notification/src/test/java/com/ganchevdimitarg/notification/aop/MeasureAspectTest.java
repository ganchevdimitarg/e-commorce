package com.ganchevdimitarg.notification.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeasureAspectTest {

    @Test
    void should_notThrowAndReturnResult_when_invokedConcurrently() throws Exception {
        MeasureAspect aspect = new MeasureAspect();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                futures.add(executor.submit(() -> {
                    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
                    Signature sig = mock(Signature.class);
                    when(sig.getName()).thenReturn("sendMail");
                    when(pjp.getSignature()).thenReturn(sig);
                    try {
                        when(pjp.proceed()).thenReturn("ok");
                        return aspect.measure(pjp);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }));
            }
            for (Future<Object> f : futures) {
                assertThat(f.get()).isEqualTo("ok");
            }
        }
    }

    @Test
    void should_rethrow_when_proceedThrows() throws Throwable {
        MeasureAspect aspect = new MeasureAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn("sendMail");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.measure(pjp)).isInstanceOf(IllegalStateException.class);
    }
}
