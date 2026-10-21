package com.ganchevdimitarg.catalog.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler method as rate limited.
 *
 * <p>{@link RateLimitInterceptor} reads the annotation from the resolved handler method before
 * the controller runs, so the annotation only takes effect on {@code @RequestMapping} methods.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitBucket value();
}
