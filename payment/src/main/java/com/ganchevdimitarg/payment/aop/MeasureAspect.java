package com.ganchevdimitarg.payment.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Slf4j
public class MeasureAspect {

    @Pointcut("execution(* com.ganchevdimitarg.payment.controller.*.*(..))")
    private void trackAllControllers() {
    }

    @Around("trackAllControllers()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            log.info("Method named: \"{}\" finished in {}ms.", method, elapsedMillis(start));
            return result;
        } catch (Throwable ex) {
            log.info("Method named: \"{}\" finished with exception: \"{}\" in {}ms.",
                    method, ex.toString(), elapsedMillis(start));
            throw ex;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
