package com.ganchevdimitarg.order.aop;


import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.excaption.InvalidRequestDataException;
import com.ganchevdimitarg.order.validation.ValidateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ValidationRequestDtoAspect {

    private final ValidateRequest validateRequest;

    @Around("@annotation(com.concordeu.order.annotation.ValidationRequest)")
    public Object validateRequest(ProceedingJoinPoint pjp) throws Throwable {

        log.info("Validate request data - {}", pjp.getSignature());

        Object[] requestObject = pjp.getArgs();

        if (requestObject.length > 0 && requestObject[0] instanceof OrderDto orderDto) {
            validateRequest.validateRequest(orderDto);
        } else {
            log.info("request object is not of OrderDto instance");
            throw new InvalidRequestDataException("Request object is not of OrderDto instance");
        }
        log.info("request data is valid");
        return pjp.proceed();
    }
}
