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
   - Never H2 — Testcontainers only
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

## Kafka JSON integration tests
Kafka integration tests must assert JSON deserialization correctness:
- Consume the JSON event, deserialise to the event record (e.g. `ProductEvent`), assert every field
- Assert every required field — never only assert the record exists

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
PostgreSQL 16, Redis 7, and Kafka are started once per suite via @Container static fields.
@DynamicPropertySource wires all three into the Spring context automatically.

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
- H2
- Redeclare Testcontainers — extend AbstractIntegrationTest
- spring.flyway.enabled=false in test profiles
- Reactive types (Mono, Flux) in tests for non-gateway services — WebMVC only outside api-gateway