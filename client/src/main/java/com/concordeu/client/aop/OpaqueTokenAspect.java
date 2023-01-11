package com.concordeu.client.aop;

import com.concordeu.client.dao.OpaqueTokenDao;
import com.concordeu.client.domain.OpaqueToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OpaqueTokenAspect {
    private final OpaqueTokenDao opaqueTokenDao;

    @Around("execution(* com.concordeu.client.principal.*.*(..))")
    public Object addTokenExp(ProceedingJoinPoint point) throws Throwable {
        Object[] pointArgs = point.getArgs();

        if (pointArgs.length > 0 && pointArgs[0] instanceof String token) {
            Optional<OpaqueToken> opaqueToken = opaqueTokenDao.findByToken(token);
            Instant currentTime = Instant.ofEpochSecond(Long.parseLong(new Date(System.currentTimeMillis()).toString()));
            if (opaqueToken.isEmpty()) {
                opaqueTokenDao.insert(OpaqueToken.builder()
                        .token(token)
                        .iat(currentTime)
                        .exp(Instant.ofEpochSecond(Long.parseLong(new Date(System.currentTimeMillis() + 1000 * 60 * 10).toString())))
                        .build());
                log.info("Added expiration time to token");
                return point.proceed();
            }
            if (currentTime.isAfter(opaqueToken.get().getExp())) {
                log.debug("The token has expired: " + opaqueToken.get().getToken());
                throw new BadOpaqueTokenException("The token has expired: " + opaqueToken.get().getToken());
            }
        }
        return point.proceed();
    }
}
