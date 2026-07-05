package com.ganchevdimitarg.notification.aop;

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

    @Pointcut("execution(* com.ganchevdimitarg.notification.controller.*.*(..))")
    private void trackAllControllers() {}

    @Around("trackAllControllers()")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.info("Method \"{}\" finished in {}ms",
                    pjp.getSignature().getName(), (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (Throwable ex) {
            log.info("Method \"{}\" failed with \"{}\" after {}ms",
                    pjp.getSignature().getName(), ex.toString(), (System.nanoTime() - start) / 1_000_000);
            throw ex;
        }
    }
}
