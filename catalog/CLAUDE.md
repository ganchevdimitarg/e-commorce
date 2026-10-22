# CLAUDE.md — catalog-service

> `catalog` owns the **product catalogue** (products, categories, comments) in the
> `com.ganchevdimitarg:e-commerce` Spring Boot 4.1.0 / Java 25 monorepo. Package
> `com.ganchevdimitarg.catalog`. Port **8084**. Backed by **PostgreSQL**. Stateless
> OAuth2 **resource server**, registered with Eureka, configured via Spring Cloud Vault,
> traced via OpenTelemetry (OTLP). It is the most fully migrated business service — the
> reference for the conventions the others migrate toward.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo root**:
`../CLAUDE.md` and `../docs/context/`. This file records only catalog-specific deltas —
read the root file first.

## Test baseline (recorded 2026-10-22 on `catalog-hardening-2`)

From the repo root (Git Bash):

```bash
cd catalog
# Unit tests only — tests tagged "unit"
./mvnw clean test -Dgroups=unit
# Full verify — unit + Testcontainers ITs + JaCoCo gates (Docker required)
./mvnw clean verify
```

From the repo root, the aggregator form also works:

```bash
./mvnw -pl catalog -am clean verify
```

- How tests run: `maven-surefire-plugin` includes `**/*Test.java` and `**/*IT.java`;
  `maven-failsafe-plugin` is **not bound**. The unit/full split uses the JUnit
  `unit` / `integration` tags (`-Dgroups=unit`).
- Unit: `281` run, `0` failures, `0` errors (catalog module).
- Full verify: `460` run, `0` failures, `0` errors.
  - Status: green. JaCoCo gates met (BUNDLE ≥ 90 % line, `service.*` = 100 %).

## Module specifics
- Web stack: WebMVC (records for DTOs/commands/responses); MapStruct (`MapStructMapper`)
  for entity↔DTO; springdoc-openapi + Swagger UI (OAuth2 PKCE).
- Security: resource server validating **JWTs** issued by the auth service (issuer `:8082`);
  social opaque tokens terminate at auth/gateway. Authorise on
  `SCOPE_catalog.read` / `SCOPE_catalog.write`.
- API: `/api/v1/catalog/...`. Secrets from Vault as `${ENV_VAR}` — never inline.
- Pagination max-100 enforced by `PageableSupport.capped()` (oversized → 400).
- **No MongoDB, no Avro / schema registry** in catalog — do not scaffold them here.

## Hardening-run rules (TODO.md sessions)

Session rules for the `catalog/TODO.md` hardening list — restated from the root
`CLAUDE.md` so each session sees them without cross-module context:

- **Scope**: stay inside `catalog/` — never edit other modules. Base package:
  `catalog/src/main/java/com/ganchevdimitarg/catalog`.
- **Flyway**: migrations only under `src/main/resources/db/migration/`; never edit a
  committed migration — add the next free version (the highest existing `V<n>` plus one;
  list the directory to confirm); `IF [NOT] EXISTS`
  guards; `ddl-auto=validate`; Flyway owns the schema.
- **Tests**: JUnit 5 + AssertJ, names `should_<expected>_when_<condition>`; write the
  failing test first, then the fix; ITs extend `AbstractIntegrationTest` /
  `RedisKafkaIntegrationBase` (Testcontainers — Docker must be running); never
  H2/EmbeddedMongo; Awaitility, never `Thread.sleep()`.
- **Errors**: domain exceptions extend `BusinessException` and map to problem+json
  (RFC 9457) via `@ControllerAdvice`; no raw 500s; never catch a `BusinessException`
  and rethrow as `RuntimeException`.

## Catalog-specific infrastructure (load the doc on demand)
- **Redis** — `Idempotency-Key` guard (`catalog:idempotency:<key>`, 24h TTL, duplicate → 409)
  and product read cache. NB catalog uses Spring `@Cacheable` on `getProductById` plus
  after-commit `ProductCache.put`/`evict`/`evictAll` (10-min TTL, namespace `catalog:v3:`), a
  deliberate deviation from the root cache-aside rule; it declares no `@CacheEvict`.
  → `../docs/context/caching.md`
- **Kafka** — product events on the four topics `catalog.product.created`, `.updated`,
  `.deleted` and `.category-changed`, **JSON** via `StringSerializer` over pre-serialised
  payloads (no external registry; version-dispatched JSON Schemas with a BACKWARD
  compatibility gate), written to a transactional outbox and relayed asynchronously;
  sealed `ProductEvent` record subtypes; acks=all + idempotence.
  → `../docs/context/kafka-patterns.md`
- **Read replica** — `DataSourceRouter extends AbstractRoutingDataSource`, routes on
  `isCurrentTransactionReadOnly()`; writer (:5432) / reader (:5433) pools,
  `LazyConnectionDataSourceProxy`, 2s health probe, graceful fallback.
  → `../docs/context/read-replica-patterns.md`
- **Observability** — Micrometer → Prometheus; metrics `catalog.<entity>.<action>`; OTLP
  tracing + log export; actuator exposure narrowed to `health,info,prometheus`.
  → `../docs/context/otlp-patterns.md`
- **CI/CD** — Jenkins declarative pipeline in `catalog/Jenkinsfile` (job Script Path,
  `changeset "catalog/**"` gates, wrapper build, `catalog/` Docker context); images
  `catalog:<git-sha>`; PR branches skip deploy. → `../docs/context/cicd-patterns.md`

## Local development
```bash
cd catalog
./mvnw clean verify                 # standalone (preferred)
./mvnw spring-boot:run              # standalone run
# from the repo root, still supported:
./mvnw -pl catalog -am clean verify
```

The full stack also runs without any sibling module:

```bash
cp .env.example .env
docker compose -f compose-standalone.yaml up -d --build   # catalog :8084, mock OIDC :8090, Kafka :29092
docker compose -f compose-standalone.yaml down -v         # teardown, drops the database volume
```

Runtime configuration is environment-driven. The `dev` profile (`application-dev.yml`)
supplies localhost defaults and is active when no profile is set; the `prod` profile
(`application-prod.yml`) requires the external endpoints from the environment, disables
Vault, Docker Compose, devtools and Swagger, and must be selected with
`SPRING_PROFILES_ACTIVE=prod`.

Environment variables (dev defaults in brackets): `POSTGRES_USER` and `POSTGRES_PASSWORD`
(required), `SERVER_PORT` [8084], `AUTH_ISSUER_URI` [http://localhost:8082],
`AUTH_JWK_SET_URI` [http://localhost:8082/oauth2/jwks], `REDIS_HOST` [localhost],
`REDIS_PORT` [6379], `KAFKA_BOOTSTRAP_SERVERS` [localhost:9092], `EUREKA_CLIENT_ENABLED`
[true], `EUREKA_DEFAULT_ZONE` [http://localhost:8761/eureka], `SPRING_CLOUD_VAULT_ENABLED`
[true], `VAULT_URI` [http://localhost:8200], `VAULT_BACKEND` [secret],
`VAULT_DEFAULT_CONTEXT` [catalog/credentials], `OTLP_TRACES_ENDPOINT`
[http://localhost:4318/v1/traces], `CATALOG_DB_WRITER_URL`
[jdbc:postgresql://localhost:5432/catalog] and `CATALOG_DB_READER_URL`
[jdbc:postgresql://localhost:5433/catalog]`.

Vault is optional in dev (`spring.config.import: optional:vault://`, `fail-fast: false`)
and catalog owns its KV path `secret/catalog/credentials` (mount `secret`, context
`catalog/credentials`). Seed it with:

```bash
vault kv put secret/catalog/credentials POSTGRES_USER=catalog POSTGRES_PASSWORD=catalog
```

Flyway applies and validates migrations at application startup; there is no
`flyway-maven-plugin` configured, so `./mvnw flyway:validate` is not available.

Docker image: the build context is `catalog/` — run
`docker build -t catalog:<tag> catalog/` from the repo root (or
`docker build -t catalog:<tag> .` from `catalog/`). The Dockerfile builds with the
in-context wrapper and copies the explicit artifact
`target/catalog-0.0.1-SNAPSHOT.jar`; no `-pl catalog -am` is used.

catalog auto-manages its own data plane (PG writer/reader, Redis, Kafka) via
`catalog/compose.yaml` on `spring-boot:run` (dev only; tests use Testcontainers). Only
shared infrastructure (Vault `:8200` and the other root-compose services) comes from the
root `docker-compose.yml`; Eureka (`:8761`) and auth (`:8082`) are started from their own
modules, and an OTLP collector (`:4318`) must be supplied separately. Env:
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `VAULT_DEV_ROOT_TOKEN_ID`.

## Known migration gaps
- Source fully migrated to Boot 4.1.0 / Java 25 on the single `com.ganchevdimitarg.catalog`
  package — no `com.concordeu` remnant. `clean verify` is green (322 tests, JaCoCo 90 %
  bundle / 100 % `service.*`). The reference module.
- catalog has its own POM parent (`spring-boot-starter-parent:4.1.0`) and its own Maven
  wrapper, so it builds standalone from `catalog/`. It remains listed in the root
  aggregator, and the root `-pl catalog -am` build stays supported.
- The Docker build is self-contained: the build context is `catalog/`
  (`docker build -t catalog:<tag> catalog/`), the image copies the explicit artifact
  `target/catalog-0.0.1-SNAPSHOT.jar`, and no `-pl catalog -am` is used. The catalog-owned
  `catalog/Jenkinsfile` now exists and is changeset-gated; the root `Jenkinsfile`'s
  `-f catalog/Dockerfile .` stage remains incompatible until the root-pipeline cutover
  (outside `catalog/`).
- Follow-ups (not blocking, human decision): no checkstyle plugin/config yet (stated PR
  gate); integration tests run under surefire (failsafe unbound). The `prod` profile added
  with TODO 7.4 is actuator-hardened: exposure `health,info,prometheus`,
  `show-details: when-authorized`, OTLP tracing.
