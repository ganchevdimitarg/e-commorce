package com.ganchevdimitarg.catalog.aop;

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

    @Pointcut("execution(* com.ganchevdimitarg.catalog.controller.*.*(..))")
    private void trackAllControllers() {}

    @Around("trackAllControllers()")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        String method = joinPoint.getSignature().getName();
        try {
            Object result = joinPoint.proceed();
            log.info("Method named: \"{}\" finished in {}ms.", method, elapsedMillis(startNanos));
            return result;
        } catch (Throwable ex) {
            log.info("Method named: \"{}\" finished with exception: \"{}\" in {} milliseconds.",
                    method, ex.toString(), elapsedMillis(startNanos));
            throw ex;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
