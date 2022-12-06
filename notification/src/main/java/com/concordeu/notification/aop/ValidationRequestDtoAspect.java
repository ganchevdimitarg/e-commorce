package com.concordeu.notification.aop;

import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.excaption.InvalidRequestDataException;
import com.concordeu.notification.validation.ValidateRequest;
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

    @Around("@annotation(com.concordeu.notification.annotation.ValidationRequest)")
    public Object validateRequest(ProceedingJoinPoint pjp) throws Throwable {

        log.info("Validate request data - " + pjp.getSignature());

        Object[] requestObject = pjp.getArgs();

        if (requestObject.length > 0 && requestObject[0] instanceof NotificationDto notificationDto) {
            validateRequest.validateRequest(notificationDto);
        } else {
            log.info("request object is not of UserRequestDto instance");
            throw new InvalidRequestDataException("Request object is not of UserRequestDto instance");
        }
        log.info("request data is valid");
        return pjp.proceed();
    }
}
