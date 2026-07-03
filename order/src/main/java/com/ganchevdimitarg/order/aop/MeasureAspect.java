package com.ganchevdimitarg.order.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Aspect
@Slf4j
public class MeasureAspect {

    @Pointcut("execution(* com.ganchevdimitarg.order.controller.*.*(..))")
    private void trackAllControllers() {
    }

    @Around("trackAllControllers()")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long startNanos = System.nanoTime();
        String method = pjp.getSignature().getName();
        try {
            Object result = pjp.proceed();
            log.info("Method named: \"{}\" finished in {}ms.", method, elapsedMillis(startNanos));
            return result;
        } catch (Throwable ex) {
            log.info("Method named: \"{}\" finished with exception: \"{}\" in {}ms.",
                    method, ex.toString(), elapsedMillis(startNanos));
            throw ex;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
