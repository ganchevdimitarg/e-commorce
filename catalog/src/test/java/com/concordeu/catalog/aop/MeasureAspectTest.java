package com.concordeu.catalog.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@Tag("unit")
class MeasureAspectTest {

    private MeasureAspect aspect;
    private JoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        aspect = new MeasureAspect();
        joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
    }

    @Test
    void should_startAndStopSuccessfully_when_normalExecution() {
        aspect.startMeasureTime();

        assertThatCode(() -> aspect.stopMeasureTimeSucceed(joinPoint))
                .doesNotThrowAnyException();
    }

    @Test
    void should_startAndStopWithException_when_methodThrows() {
        RuntimeException ex = new RuntimeException("boom");
        aspect.startMeasureTime();

        assertThatCode(() -> aspect.stopMeasureTimeException(joinPoint, ex))
                .doesNotThrowAnyException();
    }

    @Test
    void should_logMethodName_when_afterReturning() {
        aspect.startMeasureTime();
        aspect.stopMeasureTimeSucceed(joinPoint);

        verify(joinPoint.getSignature()).getName();
    }

    @Test
    void should_logMethodNameAndException_when_afterThrowing() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        aspect.startMeasureTime();
        aspect.stopMeasureTimeException(joinPoint, ex);

        verify(joinPoint.getSignature()).getName();
    }
}
