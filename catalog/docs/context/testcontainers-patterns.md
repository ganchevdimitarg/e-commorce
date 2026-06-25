# Testcontainers patterns — catalog service

## AbstractIntegrationTest — Postgres only (extend this)

Singleton-container pattern: Postgres starts once in a static initialiser and is never
stopped by the JUnit extension. All subclasses share the cached Spring context and its
Hikari pool. Wired via `@ServiceConnection` — no `@DynamicPropertySource` needed for the
datasource.

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static { POSTGRES.start(); }
}
```

## RedisKafkaIntegrationBase — adds Redis + Kafka

Extends `AbstractIntegrationTest`. Redis and Kafka are per-class `@Container` fields
managed by the inherited `@Testcontainers` extension. Use this base for any IT that
exercises caching or event publishing.

```java
public abstract class RedisKafkaIntegrationBase extends AbstractIntegrationTest {

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void redisKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host",            REDIS::getHost);
        registry.add("spring.data.redis.port",            () -> REDIS.getMappedPort(6379));
        registry.add("kafka.bootstrapAddress",            KAFKA::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers",    KAFKA::getBootstrapServers);
    }
}
```

## WireMock for HTTP clients
```java
@RegisterExtension
static WireMockExtension wm = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

@TestConfiguration
static class TestConfig {
    @Bean ClientConfig clientConfig() { return new ClientConfig(wm.baseUrl()); }
}

@Test
void should_returnProduct_when_inventoryResponds() {
    wm.stubFor(get("/inventory/123").willReturn(okJson("""{"stock":5}""")));
    // assert ...
}
```

## Async Kafka assertions (Awaitility)
```java
await().atMost(10, SECONDS)
       .pollInterval(200, MILLISECONDS)
       .untilAsserted(() -> assertThat(repo.findByCorrelationId(id)).isPresent());
```

## Rules
- Extend `AbstractIntegrationTest` for Postgres-only tests
- Extend `RedisKafkaIntegrationBase` when Redis or Kafka is needed
- Never redeclare containers — use the hierarchy
- Postgres: singleton pattern (`static { start(); }`) — survives Spring context caching
- Redis + Kafka: per-class `@Container` lifecycle
- `@ActiveProfiles("test")` is inherited from the base class
