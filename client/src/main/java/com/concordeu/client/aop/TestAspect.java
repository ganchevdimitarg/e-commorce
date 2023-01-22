package com.concordeu.client.aop;

import com.concordeu.client.dao.OpaqueTokenDao;
import com.concordeu.client.domain.OpaqueToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TestAspect {

    @Before("execution(* com.concordeu.client.introspector.CustomOpaqueTokenIntrospector.introspect(..))")
    public void addTokenExp()  {
        System.out.println("Yes! It is working!");
    }
}
