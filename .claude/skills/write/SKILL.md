---
name: write
description: Implement a feature end-to-end following project conventions. Triggers on /write.
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---

## Steps

1. Read relevant existing code first — service, repository, domain model, existing tests.
2. Plan: list every file to create or modify. If schema changes are needed, state the next Flyway version number.
3. Implement in this order: Flyway migration → domain model → repository → service → controller.
   Schema-first: migration runs first so `flyway:validate` passes from the start.
4. Use records for all immutable types (DTOs, commands, responses, events, value objects). Apply Lombok only when record is insufficient: @Getter+@Setter+@NoArgsConstructor on JPA entities; @RequiredArgsConstructor on @Service; @Slf4j for logging.
5. Use Java 25 features: records for data carriers, sealed+switch for unions, ScopedValue for request context, StructuredTaskScope for parallel calls.
6. Write unit tests (JUnit 5 + AssertJ, real domain objects, no Mockito on domain logic).
7. Write integration tests extending AbstractIntegrationTest (Testcontainers — never H2).
8. Add observability: MDC traceId/userId at entry point, structured log on key operations, MeterRegistry counter for significant actions.
9. Run: `./mvnw clean verify` (single-module repo; for a future monorepo add `-pl <module> -am`)
10. If verify fails:
    - Attempt to fix the root cause.
    - If the failure is a Flyway version conflict, migration checksum error, or unresolvable compilation error:
      revert partial changes with `git restore src/` before stopping.
    - Report exactly what blocked and what was reverted. Do not leave the repo in a broken state.
    - Do not suppress errors or skip tests to force a pass.
11. Re-run verify after every fix. Do not stop until fully green.

## Convention rules — single source of truth

This skill governs the **process** (implementation order + the verify loop above).
The **rules** for Lombok, records, Flyway, Redis, Avro/Schema Registry, Kafka,
validation, pagination, and security live in `CLAUDE.md` and its `@import` files,
which are always in context. Do not restate them here — follow them as written
there. If a rule and this skill ever disagree, `CLAUDE.md` wins.