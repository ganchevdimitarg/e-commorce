# Observability

Cross-cutting telemetry conventions. catalog implements these today (Micrometer →
Prometheus + OpenTelemetry/OTLP); other services adopt them as they migrate.

- All services include `spring-boot-starter-actuator` + the Micrometer OpenTelemetry
  bridge; traces exported over OTLP.
- Trace context propagated via the W3C `traceparent` header on all HTTP and Kafka messages.
- MDC keys, set at request entry and cleared on exit: `traceId`, `spanId`, `userId`,
  `serviceId`.
- Structured JSON logging (Logback + logstash-logback-encoder) — no plain-text log
  format in prod.
- Kafka consumers set MDC from message headers before processing and clear it after.
- Custom metrics via `MeterRegistry`, named `<service>.<entity>.<action>`
  (e.g. `catalog.product.created`, `order.payment.retried`).
- Health indicators (DB, Redis, Kafka) exposed at `/actuator/health`; details restricted
  to internal/authorized callers. Narrow actuator exposure (e.g. `health,info,prometheus`).

## MDC request filter

```java
@Component
public class MdcRequestFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put("traceId", req.getHeader("traceparent"));
            MDC.put("userId", req.getHeader("X-User-Id"));
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```
