# OTLP observability patterns

Tracing via the Micrometer OpenTelemetry bridge, exported over OTLP. Metrics via Prometheus.
Structured logging with trace context in MDC.

## Dependencies

| Artifact | Purpose |
|---|---|
| `micrometer-tracing-bridge-otel` | Micrometer to OpenTelemetry bridge |
| `opentelemetry-exporter-otlp` | OTLP trace exporter |
| `micrometer-registry-prometheus` | Prometheus metrics registry |
| `opentelemetry-logback-appender-1.0` (`${otel-logback.version}` = `2.21.0-alpha`) | Ships logs to the OTel Collector with trace context |

## Application config (`application-dev.yml`)

```yaml
management:
  tracing:
    sampling:
      probability: 0.1
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  endpoint:
    health:
      show-details: when-authorized
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  prometheus:
    metrics:
      export:
        enabled: true
```

Tracing endpoint: `http://localhost:4318/v1/traces` (OTLP HTTP). Sampling: 10%.

## Logback (`logback-spring.xml`)

**OTEL appender** — always active, ships logs with MDC attributes to the OTel Collector:

```xml
<appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>true</captureExperimentalAttributes>
    <captureMdcAttributes>*</captureMdcAttributes>
</appender>
```

**dev | default** — plain console with trace/user context + OTEL:

```xml
<springProfile name="dev | default">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%X{traceId:-},%X{spanId:-}] [user=%X{userId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>
</springProfile>
```

**prod** — JSON encoder + OTEL:

```xml
<springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>
</springProfile>
```

## MDC keys

`MdcRequestFilter` (`@Component`, extends `OncePerRequestFilter`) sets keys at request entry
and clears them in a `finally` block:

| MDC key | Source | Notes |
|---|---|---|
| `traceId` | `traceparent` header | W3C trace-context propagation |
| `userId` | `X-User-Id` header | Empty string if absent |
| `serviceId` | Hardcoded `catalog-service` | Static constant |
| `spanId` | Set by Micrometer tracing bridge | Not set by the filter; injected automatically |

```java
@Component
public class MdcRequestFilter extends OncePerRequestFilter {
    private static final String SERVICE_ID = "catalog-service";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceparent = request.getHeader("traceparent");
            if (traceparent != null) { MDC.put("traceId", traceparent); }
            MDC.put("userId", headerOrEmpty(request, "X-User-Id"));
            MDC.put("serviceId", SERVICE_ID);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

## Custom metrics

Constructor-injected `MeterRegistry`. Counter naming: `catalog.<entity>.<action>`.

Examples from `ProductServiceImpl`:
- `meterRegistry.counter("catalog.product.created").increment()`
- `meterRegistry.counter("catalog.product.updated").increment()`
- `meterRegistry.counter("catalog.product.deleted").increment()`

Error counter from `ProductEventPublisher`:
- `meterRegistry.counter("catalog.event.send.failed", "topic", topic).increment()`
