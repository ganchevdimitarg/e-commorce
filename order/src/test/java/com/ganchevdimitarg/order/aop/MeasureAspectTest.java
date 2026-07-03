package com.ganchevdimitarg.order.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeasureAspectTest {

    @Test
    void should_returnTargetResultAndNotThrow_when_invokedConcurrently() throws Throwable {
        MeasureAspect aspect = new MeasureAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn("createOrder");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenReturn("ok");

        // Two virtual threads through the same aspect instance must not corrupt shared state.
        var t1 = Thread.ofVirtual().start(() -> { try { aspect.measure(pjp); } catch (Throwable ignored) { } });
        Object result = aspect.measure(pjp);
        t1.join();

        assertThat(result).isEqualTo("ok");
    }
}
