package com.concordeu.payment.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Aspect
@Slf4j
public class MeasureAspect {
    private final StopWatch stopWatch = new StopWatch();

    @Pointcut("execution(* com.concordeu.payment.controller.*.*(..))")
    private void trackAllControllers() {}

    @Before("trackAllControllers()")
    public void startMeasureTime() {
        stopWatch.start();
    }

    @AfterReturning("trackAllControllers()")
    public void stopMeasureTimeSucceed(JoinPoint joinPoint) {
        stopWatch.stop();
        log.info("Method named: \"{}\" finished in {}ms.",
                joinPoint.getSignature().getName(), stopWatch.getLastTaskTimeMillis());
    }

    @AfterThrowing(value = "trackAllControllers()", throwing = "ex")
    public void stopMeasureTimeException(JoinPoint joinPoint, Throwable ex) {
        stopWatch.stop();
        log.info("Method named: \"{}\" finished with exception: \"{}\" in {} milliseconds.",
                joinPoint.getSignature().getName(), ex.toString(), stopWatch.getLastTaskTimeMillis());
    }


}
