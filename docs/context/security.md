# Spring Security patterns

## SecurityFilterChain bean — stateless, CSRF disabled
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .build();
    }
}
```

## @PreAuthorize on service layer
```java
@EnableMethodSecurity // on SecurityConfig class
// then on service methods — never on controllers:
@PreAuthorize("hasRole('ADMIN')")
public void cancelOrder(UUID orderId) { ... }
```

## Header-based identity — reading gateway-injected headers
```java
@Component
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = req.getHeader("X-User-Id");
        String roles  = req.getHeader("X-User-Roles");
        // Store in ScopedValue or pass via method params — never ThreadLocal
        chain.doFilter(req, res);
    }
}
```

## MDC integration — traceId + userId
```java
@Component
public class MdcRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put("traceId", req.getHeader("traceparent"));
            MDC.put("userId", req.getHeader("X-User-Id"));
            MDC.put("serviceId", "<service-name>");
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

## Rules
- Never rely on Spring Boot auto-config defaults — always declare a `SecurityFilterChain` bean
- `@PreAuthorize` on service layer only — never on controllers
- Stateless session management — no HTTP session
- Actuator health endpoint open (`permitAll`); details `when-authorized`
- `X-User-Id` / `X-User-Roles` trusted from gateway — no re-validation in downstream services
