package com.ganchevdimitarg.order.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeasureAspectTest {

    @Test
    void should_returnTargetResultAndNotThrow_when_invokedConcurrently() throws Throwable {
        MeasureAspect aspect = new MeasureAspect();
        // Force genuine overlap: proceed() blocks on a 2-party barrier so neither measure()
        // call can complete until both are in-flight together — no reliance on scheduling luck.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn("createOrder");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenAnswer(invocation -> {
            barrier.await(5, TimeUnit.SECONDS);
            return "ok";
        });

        AtomicReference<Object> vtResult = new AtomicReference<>();
        AtomicReference<Throwable> vtError = new AtomicReference<>();
        var t1 = Thread.ofVirtual().start(() -> {
            try {
                vtResult.set(aspect.measure(pjp));
            } catch (Throwable t) {
                vtError.set(t);
            }
        });

        Object mainResult = aspect.measure(pjp);
        t1.join();

        assertThat(vtError.get()).as("virtual thread must not fail on shared aspect state").isNull();
        assertThat(mainResult).isEqualTo("ok");
        assertThat(vtResult.get()).isEqualTo("ok");
    }

    @Test
    void should_rethrowOriginalException_when_targetThrows() throws Throwable {
        MeasureAspect aspect = new MeasureAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn("createOrder");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> aspect.measure(pjp))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
