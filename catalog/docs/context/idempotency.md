# Idempotency — HandlerInterceptor + Redis SETNX

`IdempotencyInterceptor implements HandlerInterceptor` guards mutating endpoints
against duplicate requests using a Redis SETNX pattern in `preHandle()`.

## How it works

1. Client sends an `Idempotency-Key` header with the request.
2. The interceptor checks the HTTP method — only **POST, PUT, DELETE** are guarded.
3. If the header is absent or blank, the request proceeds (no 400).
4. Redis `SETNX` on key `catalog:idempotency:<key>` with 24h TTL.
5. First-seen → request proceeds. Duplicate → `ConflictException` (409).

## Interceptor (excerpt)
```java
@Slf4j
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Set<String> GUARDED = Set.of("POST", "PUT", "DELETE");
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!GUARDED.contains(request.getMethod())) return true;

        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) return true;

        Boolean firstSeen = redis.opsForValue()
                .setIfAbsent("catalog:idempotency:" + key, "1", TTL);
        if (Boolean.FALSE.equals(firstSeen)) {
            log.warn("Duplicate idempotency key: {}", key);
            throw new ConflictException("Duplicate request for Idempotency-Key: " + key);
        }
        return true;
    }
}
```

## Registration
```java
@Configuration
@RequiredArgsConstructor
public class IdempotencyConfig implements WebMvcConfigurer {

    private final StringRedisTemplate redis;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(redis))
                .addPathPatterns("/api/v1/catalog/**");
    }
}
```

## Rules
- Key scoped to the service (`catalog:idempotency:<key>`) — never per-user
- TTL: 24 hours
- Absent header = no guard (not a 400)
- Duplicate detection logged at WARN
- Fire-once guard only — no response caching; the 409 prevents re-processing
- Guarded methods: POST, PUT, DELETE (GET is not guarded)
