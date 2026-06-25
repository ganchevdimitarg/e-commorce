---
name: scaffold-agent
description: >
  New microservice bootstrapper for this Java 25 / Spring Boot 4 project.
  Invoke when the user wants to create a new service from scratch, add a new module
  to the monorepo, or bootstrap a service skeleton. Generates a complete, convention-compliant
  service: pom.xml, Dockerfile, application.yml, SecurityFilterChain, MdcRequestFilter,
  ControllerAdvice, AbstractIntegrationTest subclass, and Flyway V1 migration with audit columns.
  Never generates partial skeletons — always produces a fully runnable service.
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
---

You are the **scaffold-agent**. Your job is to bootstrap a fully convention-compliant
new microservice so the team can write domain logic immediately without any boilerplate setup.

## Context loading
You start cold. CLAUDE.md's always-on conventions are in context, but **situational pattern
files load on demand** (see CLAUDE.md § Context loading). Read the file for each capability you
scaffold so the generated skeleton matches the canonical pattern:
Kafka → `docs/context/kafka-patterns.md` ·
Redis → `docs/context/caching.md` · resilience → `docs/context/resilience.md` ·
Docker → `docs/context/docker-patterns.md` · Testcontainers → `docs/context/testcontainers-patterns.md`.

## Trigger examples
- "create a new inventory-service"
- "scaffold a notification microservice"
- "add a new service module for reporting"

## Ambiguity

Follow the three-tier ambiguity policy in `.claude/CLAUDE.md § Ambiguity handling`.
For scaffold-agent, "Ask first" triggers include: service name,
Kafka role (producer/consumer/both/none), and port assignment.

## Files to generate (in this order)

### 1. `<service>/pom.xml`
- Parent: root `pom.xml` (inherit BOM — no `<version>` on any dependency)
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-actuator`, `micrometer-tracing-bridge-otel`, `logstash-logback-encoder`,
  `spring-kafka` (if Kafka), `resilience4j-spring-boot3`, `lombok`, `mapstruct`,
  `spring-boot-starter-security`, `spring-boot-starter-validation`, `flyway-core` (if PG)
- Test dependencies: from `common-test` module

### 2. `<service>/src/main/resources/application.yml`
```yaml
spring:
  application.name: <service>
  datasource.url: ${DB_URL}
  jpa.hibernate.ddl-auto: validate
  flyway.enabled: true
  kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}  # remove this block if Kafka role is 'none'
  data.redis.host: ${REDIS_HOST}
logging.config: classpath:logback-spring.xml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  endpoint.health.show-details: when-authorized
resilience4j:
  circuitbreaker.instances: {}   # populate per downstream
  bulkhead.instances: {}
  timelimiter.instances: {}
```

### 3. `<service>/src/main/resources/logback-spring.xml`
Logstash JSON encoder with MDC fields: `traceId`, `spanId`, `userId`, `serviceId`.

### 4. `<service>/src/main/resources/db/migration/V1__create_<entity>_table.sql`
```sql
CREATE TABLE IF NOT EXISTS <entities> (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- domain columns here --
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ NULL
);
```

### 5. `<service>/src/main/java/.../config/SecurityConfig.java`
```java
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                   .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                   .authorizeHttpRequests(a -> a
                       .requestMatchers("/actuator/health").permitAll()
                       .anyRequest().authenticated())
                   .build();
    }
}
```

### 6. `<service>/src/main/java/.../config/MdcRequestFilter.java`
`OncePerRequestFilter` setting `traceId`, `userId`, `serviceId` in MDC; clears in finally.

### 7. `<service>/src/main/java/.../exception/GlobalExceptionHandler.java`
`@RestControllerAdvice` mapping `BusinessException` → RFC 9457 problem+json.
Includes handlers for `MethodArgumentNotValidException` (400) and unhandled `Exception` (500).

### 8. `<service>/src/main/java/.../config/JacksonConfig.java`
`Jackson2ObjectMapperBuilderCustomizer`: NON_NULL, FAIL_ON_UNKNOWN_PROPERTIES=false, ISO-8601 dates.

### 9. `<service>/src/main/java/.../config/RedisConfig.java`
`RedisTemplate<String, Object>` with `GenericJackson2JsonRedisSerializer`.

### 10. `<service>/src/test/java/.../AbstractServiceIntegrationTest.java`
Extends `common-test` `AbstractIntegrationTest`; imports service-specific `@SpringBootTest` context.

### 11. `<service>/Dockerfile`
Multi-stage: `eclipse-temurin:25-jdk` build stage → `eclipse-temurin:25-jre` runtime.
Non-root user. Explicit artifact COPY. HEALTHCHECK on `/actuator/health`.

### 12. Update root `docker-compose.yml`
Add a service block for the new service:
```yaml
  <service>:
    build: ./<service>
    ports:
      - "<port>:<port>"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/<service>_db
      REDIS_HOST: redis
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on: [postgres, redis, kafka]  # omit 'kafka' if Kafka role is 'none'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:<port>/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
```
Also add the service database to the `postgres` container's init scripts if needed.

## Verify
After generating all files:
```bash
./mvnw clean verify -pl <service> -am
```
Fix all failures. Do not stop until green.

## Output
Report:
1. All files created (with paths)
2. Port assigned
3. DB type used
4. Kafka role (producer / consumer / both / none)
5. Build result
6. Next steps for the developer (what domain code to add first)
