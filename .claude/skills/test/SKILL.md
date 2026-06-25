---
name: test
description: Write or fix tests for a class or feature; run suite; enforce coverage gate. Triggers on /test.
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---

## Steps

1. Read the target class and all existing tests in that module.
2. Identify gaps:
   - Unit tests: domain logic, business rules, edge cases
   - Integration tests: DB read/write, Redis cache behaviour, Kafka publish/consume
3. Unit tests — rules:
   - JUnit 5 + AssertJ only
   - Real domain objects — no Mockito on domain logic
   - Mock external HTTP clients with WireMock (not Mockito RestTemplate mocks — bypasses serialisation):
     ```java
     @RegisterExtension
     static WireMockExtension wm = WireMockExtension.newInstance()
         .options(wireMockConfig().dynamicPort()).build();

     @DynamicPropertySource
     static void props(DynamicPropertyRegistry r) {
         r.add("inventory.base-url", wm::baseUrl);
     }

     @Test
     void should_returnProduct_when_inventoryResponds() {
         wm.stubFor(get("/inventory/123").willReturn(okJson("""{"stock":5}""")));
         // call service and assert
     }
     ```
   - Test naming: `should_<expectedBehavior>_when_<condition>`
4. Integration tests — rules:
   - Always extend `AbstractIntegrationTest` from common-test module
   - Never H2 or EmbeddedMongo — Testcontainers only
   - Use `@Sql("/db/seed-<entity>.sql")` for test data setup, not Java builders in @BeforeEach
   - Clean state: prefer `@Transactional` on test method; use `@DirtiesContext` only as last resort
5. Async Kafka assertions — use Awaitility, never Thread.sleep():
   ```java
   await().atMost(10, SECONDS)
          .pollInterval(200, MILLISECONDS)
          .until(() -> repo.findByCorrelationId(id).isPresent());
   ```
6. Observability in tests: verify MDC keys are set/cleared; assert metrics incremented where relevant.
7. Run: `./mvnw test`
8. Check coverage: `./mvnw verify` — gate is 80% line, 100% domain model.
9. Fix all failures. Repeat until suite is fully green.

## Avro / Kafka integration tests
Kafka integration tests must assert Avro deserialization correctness:
```java
@Test
void should_deserializePaymentCompletedEvent_when_messageConsumed() {
    PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
        .setOrderId(UUID.randomUUID().toString())
        .setTraceId("trace-123")
        .setCorrelationId(UUID.randomUUID().toString())
        .setAmount(99.99)
        .build();
    kafkaTemplate.send("order.payment.completed", event);
    await().atMost(10, SECONDS).untilAsserted(() -> {
        var saved = orderRepo.findByCorrelationId(event.getCorrelationId().toString());
        assertThat(saved).isPresent();
        assertThat(saved.get().getAmount()).isEqualByComparingTo("99.99");
    });
}
```
- Use `MockSchemaRegistryClient` or Schema Registry Testcontainer wired via `@DynamicPropertySource`
- Assert every required field — never only assert the record exists
- Backward-compat test: produce with old schema version, consume with new — must not throw

## Spring Cloud Contract — producer verification
Run on the producing service before publishing stubs:
```bash
# 1. Generate and run contract tests on producer
./mvnw spring-cloud-contract:generateTests verify -pl <producer-module>

# 2. Install stubs to Maven local (consumed by downstream services in CI)
./mvnw install -pl <producer-module> -DskipTests
```
Add to producer `pom.xml`:
```xml
<plugin>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-contract-maven-plugin</artifactId>
  <extensions>true</extensions>
  <configuration>
    <testFramework>JUNIT5</testFramework>
    <baseClassForTests>com.example.BaseContractTest</baseClassForTests>
  </configuration>
</plugin>
```
`BaseContractTest` must set up a `MockMvc` or real `@SpringBootTest` context with stubs for dependencies.
Consumer tests use `@AutoConfigureStubRunner` pointing to installed stubs.

## Parameterized tests
Use `@ParameterizedTest` + `@MethodSource` for boundary values and multiple inputs sharing identical logic:
```java
@ParameterizedTest
@MethodSource("invalidOrderSizes")
void should_throwValidationException_when_orderSizeInvalid(int size) {
    assertThatThrownBy(() -> new CreateOrderCommand(UUID.randomUUID(), items(size)))
        .isInstanceOf(ValidationException.class);
}
static Stream<Integer> invalidOrderSizes() { return Stream.of(0, -1, 51, 100); }
```
Do NOT use `@ParameterizedTest` when:
- Each case needs distinct setup/teardown
- Cases differ in what they assert (not just inputs)
- A failure in one case should not indicate anything about others
  Use separate `@Test` methods for those.

## AbstractIntegrationTest containers
Postgres, MongoDB, Redis, Kafka are all started once per suite via @Container static fields.
The MongoDB container is present for all services — do not remove it even if the service doesn't use Mongo.
@DynamicPropertySource wires all four into the Spring context automatically.

## Flyway in tests
- Test profile runs the same migrations as prod — this validates schema parity
- Repeatable migrations (R__) provide seed data only
- Never set spring.flyway.enabled=false in any test profile
- Never set spring.jpa.hibernate.ddl-auto to anything other than validate

## Coverage rules
- Domain model classes (entities, value objects, aggregates): 100% line coverage
- Service layer: 80% minimum
- Controllers: covered via integration tests, not unit mocked tests
- Exclude: generated code, @SpringBootApplication, config classes (@Configuration)

## Never
- Thread.sleep() — always Awaitility
- H2 / EmbeddedMongo
- Redeclare Testcontainers — extend AbstractIntegrationTest
- spring.flyway.enabled=false in test profiles
- Reactive types (Mono, Flux) in tests for non-gateway services — WebMVC only outside api-gateway