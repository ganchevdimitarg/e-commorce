# Decision Log

Running log of architectural and technical decisions. For formal ADRs see `docs/adr/`.
Reference with `@docs/decisions.md` in prompts when Claude re-raises settled questions.

| Date | Decision | Alternatives rejected | Reason |
|---|---|---|---|
| 2025-06 | WebMVC for business services; WebFlux for api-gateway only | WebFlux everywhere | Virtual threads give WebMVC near-reactive throughput; simpler testing; team familiarity. See ADR-001. |
| 2025-06 | Avro + Schema Registry for all Kafka messages | JSON, Protobuf | Schema enforcement, backward compat checking, Confluent tooling. See ADR-002. |
| 2025-06 | Records as default immutable type | Lombok @Value everywhere | Language feature; no annotation processing; cleaner compact constructors. See ADR-003. |
| 2025-06 | Choreography sagas via Kafka | Conductor orchestrator, 2PC | No single point of failure; works across PG + Mongo; no extra infra. See ADR-004. |
| 2025-06 | Soft-delete via deleted_at column | Hard DELETE, status column | Audit trail; event replay; no accidental data loss; consistent across all tables. |
| 2025-06 | Flyway for all schema changes; ddl-auto=validate | Liquibase, Hibernate auto-DDL | Flyway simpler API; validate catches drift early; industry standard for Spring. |
| 2025-06 | Redis cache-aside over Spring @Cacheable | @Cacheable | Explicit TTL control; explicit serialization; easier to test; no magic. |
| 2025-06 | Idempotency-Key header on all mutating endpoints | None (at-most-once) | Prevents duplicate charges/orders on network retry; required for Kafka consumer safety. |
| 2025-06 | Testcontainers for all integration tests | H2, EmbeddedMongo | Tests run against real engines; no behaviour divergence; catches index/type issues. |
| 2025-06 | Resilience4j for circuit breaking | Hystrix (EOL), manual | Active project; Spring Boot 4 native support; annotation-driven; no extra infra. |
| 2025-06 | Virtual threads (Java 25 default) over platform threads | Reactive WebFlux everywhere | Simplifies code; `ScopedValue` replaces `ThreadLocal`; Spring Boot 4 native; no reactive complexity outside api-gateway |
| 2025-06 | Resilience4j defaults as documented (50%/2s/30s/5 calls/10 concurrent/5s timeout) | Per-service customisation from day one | Sane starting point; services override in `application.yml` only when measured data demands it |
| 2025-06 | Idempotency key scoped to service, never per-user | Per-user idempotency key | Prevents cross-user replay; keeps idempotency purely about network retries, not authorisation |

- 2026-06-20 — Convention rules have a single source of truth: `CLAUDE.md` + its `@import` files. Skills and agents reference them and govern process only; they do not restate rules. `review/SKILL.md`'s severity checklist is the one allowed alternative representation (it adds Critical/Warning/Suggestion tagging). Rationale: kill three-way drift flagged in the 2026-06-20 setup audit.
- 2026-06-21 — The project-local `.claude/skills/brainstorming/` fork is canonical; `superpowers:brainstorming` is superseded in this repo. Its `description:` keeps the upstream activation trigger verbatim but appends a "Project-canonical fork" marker so the two skills no longer share byte-identical activation text. Alternatives rejected: keeping the description byte-identical (left activation non-deterministic — the plugin copy could fire instead of the 97/100 fork). Rationale: closes the dual-activation ambiguity flagged in the 2026-06-21 setup evaluation.

## 2026-06-25 — Grade-A hardening

- **Audit columns on entity tables only.** `created_at`/`updated_at`/`deleted_at` added to the five
  entity tables (`clients`, `grant_types`, `scopes`, `redirect_uris`, `token_settings`) via the
  `Auditable` `@MappedSuperclass`. Pure association tables (`clients_scopes`, `clients_grant_types`,
  `clients_token_settings`) are exempt — they carry no independent lifecycle.
- **Signing key generated-and-persisted to files, never committed.** The RSA JWK is read from (or, on
  first run, generated into) a git-ignored directory as PKCS#8 + X.509 DER files with a
  thumbprint-derived `kid`, stable across restarts and instances sharing the directory. Production
  delivers the files via Vault. Replaces per-boot `KeyPairGenerator`, which invalidated live tokens on
  restart and broke JWK validation between instances.
- **Issuer externalised** via `auth.issuer-uri` so the `iss` claim is correct behind the gateway.
- **RegisteredClientRepository returns null on miss** (framework contract) rather than throwing — an
  unknown or malformed client id yields a proper OAuth2 `invalid_client` error, not a 500.
- **Spring Cloud compatibility verifier disabled.** The monorepo runs Boot 4.1.0 ahead of the matching
  Spring Cloud release train; the verifier otherwise fails context startup. Disabled in `bootstrap.yml`
  (and the test bootstrap) until the Cloud train catches up.
- **AuthUser authorities stored as `Set<String>`.** Previously persisted as Spring Security
  `GrantedAuthority` instances, which MongoDB cannot deserialise (login failed end-to-end). Role names
  are stored as strings and mapped to `SimpleGrantedAuthority` in `getGrantedAuthorities()`.
- **Deferred — `ClientService.save()` does not persist correctly.** The over-normalised `@ManyToMany`
  model makes `Client` the inverse side of the joins and never sets the `redirect_uris.client_id`
  back-reference, so dynamic registration violates the FK. This deployment seeds clients via Flyway
  (`V2`), so `save()` is unused at runtime; the read path is covered by `ClientServicePersistenceIT`
  against the seeded `gateway` client. A full fix (collapse to `JdbcRegisteredClientRepository`, or fix
  ownership + cascade) is deferred as a higher-risk structural change.
- **Deferred — profile fields on `AuthUser`** (`firstName`/`lastName`/`address`/`phoneNumber`) belong
  to the `profile` service's bounded context; extraction deferred to avoid breaking unverified consumers.
- **Soft-delete filter intentionally omitted on OAuth config tables.** The `deleted_at` audit column
  exists for convention uniformity, but client/scope/grant-type/redirect/token-setting rows are static
  configuration that is never soft-deleted (clients are seeded, not deleted at runtime), so queries do
  not filter `WHERE deleted_at IS NULL`. Revisit alongside the `save()`/ownership rework if a delete
  path is ever added.
