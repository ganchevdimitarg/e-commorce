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
- 2026-07-07 — Boot-4 Grade-A remediation. Committed the Boot-4 migration (security lambda DSL + dual JWT/opaque resolver, `PaymentGateway` port over Stripe, problem+json exception model, record DTOs, V2 audit columns + optimistic locking). Charge safety: the Stripe call runs OUTSIDE the DB transaction with a Stripe idempotency key (retry cannot double-charge), and the local row is written by a separate `@Transactional` persistence bean (avoids the self-invocation proxy trap). Restored the full-refund endpoint — `order`'s post-charge compensation saga depends on it. Removed H2 (Testcontainers-only), swapped the reactive resilience4j starter for the servlet one, added maven-failsafe + JaCoCo gates (80% line / 100% domain, 94% actual) and a Redis Testcontainer. Excluded `ReactiveOAuth2ResourceServerAutoConfiguration` — `reactor-core` leaks transitively (resilience4j) and mis-fires a reactive opaque-token introspector that crashes context boot in this WebMVC service. Follow-ups: HTTP idempotency remains store-key-only (409-on-duplicate), consistent with auth/order/catalog; Stripe outbound calls are not yet wrapped with `@CircuitBreaker`/`@Bulkhead` (systemic, pre-existing); `IdempotencyInterceptor` guards POST/PUT/DELETE but not PATCH (no PATCH endpoints today).
