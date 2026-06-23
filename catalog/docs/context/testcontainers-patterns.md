# Testcontainers patterns — load with: @.claude/context/testcontainers-patterns.md

## AbstractIntegrationTest (extend this — never redeclare containers)
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Container static final MongoDBContainer       mongo    = new MongoDBContainer("mongo:7");
    @Container static final GenericContainer<?>    redis    = new GenericContainer<>("redis:7").withExposedPorts(6379);
    @Container static final KafkaContainer         kafka    = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",          postgres::getJdbcUrl);
        r.add("spring.data.mongodb.uri",         mongo::getReplicaSetUrl);
        r.add("spring.data.redis.host",          redis::getHost);
        r.add("spring.data.redis.port",          () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers",  kafka::getBootstrapServers);
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

## Async Kafka assertions
```java
await().atMost(10, SECONDS)
       .pollInterval(200, MILLISECONDS)
       .untilAsserted(() -> assertThat(repo.findByCorrelationId(id)).isPresent());
```
