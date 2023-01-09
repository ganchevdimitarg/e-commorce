package com.concordeu.profile.aop;

import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.validation.ValidateData;
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
public class ValidationRequestAspect {

    private final ValidateData validateData;

    @Around("@annotation(com.concordeu.profile.annotation.ValidationRequest)")
    public Object validateRequest(ProceedingJoinPoint pjp) throws Throwable {

        log.info("Validate request data - " + pjp.getSignature());

        Object[] requestObject = pjp.getArgs();

        if (requestObject.length > 0 && requestObject[0] instanceof UserRequestDto authUser) {
            validateData.validateRequest(authUser);
        } else {
            log.info("request object is not of UserRequestDto instance");
            throw new InvalidRequestDataException("Request object is not of UserRequestDto instance");
        }
        log.info("request data is valid");
        return pjp.proceed();
    }
}
