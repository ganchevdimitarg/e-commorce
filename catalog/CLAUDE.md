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

## Module specifics
- Web stack: WebMVC (records for DTOs/commands/responses); MapStruct (`MapStructMapper`)
  for entity↔DTO; springdoc-openapi + Swagger UI (OAuth2 PKCE).
- Security: resource server validating **both JWT** (issuer `:8082`) **and opaque tokens**
  (introspection via `client`'s `CustomOpaqueTokenIntrospector`) through an
  `AuthenticationManagerResolver`. Authorise on `SCOPE_catalog.read` / `SCOPE_catalog.write`.
- API: `/api/v1/catalog/...`. Secrets from Vault as `${ENV_VAR}` — never inline.
- Pagination max-100 enforced by `PageableSupport.capped()` (oversized → 400).
- **No MongoDB, no Avro / schema registry** in catalog — do not scaffold them here.

## Catalog-specific infrastructure (load the doc on demand)
- **Redis** — `Idempotency-Key` guard (`catalog:idempotency:<key>`, 24h TTL, duplicate → 409)
  and product read cache. NB catalog uses Spring `@Cacheable`/`@CacheEvict` (10-min TTL,
  namespace `catalog:`), a deliberate deviation from the root cache-aside rule.
  → `../docs/context/caching.md`
- **Kafka** — product events on `catalog.product.{created,updated,deleted}`, **JSON** via
  `JsonSerializer` (no schema registry), published in `afterCommit()`; sealed `ProductEvent`
  record subtypes; acks=all + idempotence. → `../docs/context/kafka-patterns.md`
- **Read replica** — `DataSourceRouter extends AbstractRoutingDataSource`, routes on
  `isCurrentTransactionReadOnly()`; writer (:5432) / reader (:5433) pools,
  `LazyConnectionDataSourceProxy`, 2s health probe, graceful fallback.
  → `../docs/context/read-replica-patterns.md`
- **Observability** — Micrometer → Prometheus; metrics `catalog.<entity>.<action>`; OTLP
  tracing + log export; actuator exposure narrowed to `health,info,prometheus`.
  → `../docs/context/otlp-patterns.md`
- **CI/CD** — Jenkins declarative pipeline (`Jenkinsfile`); images `catalog:<git-sha>`;
  PR branches skip deploy. → `../docs/context/cicd-patterns.md`

## Local development
```bash
./mvnw spring-boot:run -pl catalog       # run (profile dev, port 8084)
./mvnw clean verify -pl catalog -am      # build + test
./mvnw flyway:validate -pl catalog       # migration drift
```
catalog auto-manages its own data plane (PG writer/reader, Redis, Kafka) via
`catalog/compose.yaml` on `spring-boot:run` (dev only; tests use Testcontainers). Shared
platform services (Vault `:8200`, Eureka `:8761`/`:8762` — 2 HA peers, auth `:8082`,
OTLP `:4318`) come from the root `docker-compose`. Env: `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `VAULT_DEV_ROOT_TOKEN_ID`, `EUREKA_USERNAME`, `EUREKA_PASSWORD`.

## Known migration gaps
- Source fully migrated to Boot 4.1.0 / Java 25 on the single `com.ganchevdimitarg.catalog`
  package — no `com.concordeu` remnant. `clean verify` is green (136 tests, JaCoCo 85%
  bundle / 100% `service.*`). The reference module.
- The root reactor builds green again as of 2026-07-08 (all modules on Boot 4). Building
  catalog standalone still works: `../mvnw -f catalog/pom.xml clean verify` — catalog
  depends on the `client` module, so install it once first:
  `../mvnw -f client/pom.xml install -DskipTests`.
- Follow-ups (not blocking, human decision): no checkstyle plugin/config yet (stated PR
  gate); integration tests run under surefire (failsafe unbound); the catalog Docker build
  uses `-pl catalog -am` and so also trips the broken root reactor; no `application-prod.yml`
  (the `dev` profile is already actuator-hardened: exposure `health,info,prometheus`,
  `show-details: when-authorized`, OTLP tracing).
