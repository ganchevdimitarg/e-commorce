# Inter-service communication: gRPC feasibility & config-server bottleneck prevention

**Date:** 2026-07-11
**Status:** Approved — decisions recorded below, ready for implementation planning
**Related:** `docs/spec/2026-07-10-production-readiness-review.md`, ADR-004 (choreography saga), `docs/adr/`

## Context

This review was prompted by `evaluationAndScore.md` (a 30-category production-readiness
rubric) and a prior comprehensive review already on file. Rather than re-running that
review, this session narrows in on two specific questions the user raised:

1. Is gRPC a better fit than REST for synchronous inter-service calls?
2. How do we stop `config-server` from becoming a single point of failure / bottleneck?

Findings below are ground-truth from the current codebase (two Explore agents), not
opinion. No code has been changed — this is a design document to reach a decision before
any implementation.

## Topic A — gRPC for synchronous inter-service calls

### Current state

All synchronous inter-service calls are blocking `RestClient` (JDK `HttpClient` factory),
never `WebClient`/Feign in business services (WebFlux is `gateway`-only per ADR-001):

| Caller → Callee | Site | Payload |
|---|---|---|
| `order` → `profile` | `OrderServiceImpl.java:249-256` | GET, flat `UserDto` (14 fields) |
| `order` → `catalog` | `OrderServiceImpl.java:258-266` | POST batch id lookup → `List<ProductResponseDto>` (6 fields) |
| `order` → `payment` | `ChargeServiceImpl.java:36-85` | GET customer + 2×POST (charge/refund) on `PaymentDto` |

Observations:

- Every payload is a small, flat, CRUD-style JSON DTO — no streaming, no deep nesting.
- Each call is already wrapped in its own `CircuitBreakerFactory` breaker
  (`order-profile`, `order-catalog`, `order-payment`) with 2s connect / 5s read timeouts.
  Resilience is in place regardless of transport.
- `order`'s three downstream calls (profile, catalog, payment) run **sequentially**, not
  fanned out — already flagged in the prior review as a missed
  `StructuredTaskScope` opportunity (the house-documented Java 25 pattern), independent
  of transport choice.
- URIs are hardcoded plain hosts (`http://127.0.0.1:8084/api/v1` style,
  `order/src/main/resources/application-dev.yml:166-183`), **not** `lb://` +
  Eureka-resolved. Only the gateway load-balances via Eureka. This means service
  discovery is already inconsistently applied for these exact call sites — a cheaper,
  more urgent fix than a protocol change.
- No gRPC/Protobuf currently exists anywhere in the repo (no `.proto`, no
  `grpc-spring-boot-starter`, no `io.grpc`/`protobuf` deps in any of the 8 `pom.xml`
  files). Adoption would be greenfield.

### Assessment

gRPC's real advantages — binary framing, HTTP/2 multiplexing, strict schema evolution,
bidirectional streaming — pay off most for high-throughput, latency-critical, or
streaming workloads with complex payloads. None of that is present here: call volume is
one request per order operation (not bulk/streaming), payloads are flat and small, and
Jackson/JSON is already globally configured per convention
(`docs/context/lombok-records-patterns.md`, root `CLAUDE.md` Jackson section).

Cost side: gRPC would require a parallel schema-management pipeline (`.proto` +
codegen) alongside the existing Bean-Validation/records-based REST contract convention,
a new server stack per business service (grpc-spring-boot-starter, new health-check
wiring for the mandatory Docker `HEALTHCHECK`), and a custom `ClientInterceptor`
replacement for the existing OAuth2 bearer-token `RestClient` interceptor
(`RestClientConfig.java:48-66`). It would also need version-pinning in the root BOM
against Spring Boot 4.1.0 / Spring Cloud 2025.1.1 / Java 25, with no precedent in this
codebase for that combination.

**Recommendation: do not adopt gRPC.** The actual pain points in this data (sequential
fan-out, hardcoded URIs bypassing Eureka) are transport-independent and both cheaper and
higher-leverage to fix directly:

1. Parallelize `order`'s profile/catalog/payment calls with `StructuredTaskScope`
   (already the documented house pattern, currently unused — `java25-patterns.md`).
2. Route `order`'s downstream calls through Eureka-resolved `lb://` client config instead
   of hardcoded hosts, for consistency with the gateway and for real load-balancing/
   failover when a service scales to N instances.

If a genuine high-throughput or streaming use case emerges later (e.g. real-time
inventory sync, bulk catalog export), gRPC could be piloted narrowly on that one path —
not as a blanket migration.

## Topic B — config-server bottleneck prevention

### Current state — this is the headline finding

`config-server` is **not actually consumed by any service's production configuration.**
Every business service (`order`, `profile`, `gateway`, `authentication`) imports
`optional:vault://` in `bootstrap.yml`, not `spring.cloud.config.uri` /
`spring.config.import=configserver:`. `gateway/src/main/resources/config-server-bootstrap.yml`
is entirely commented out. The only references to `configserver:` are `optional:` imports
in **test** resources (`catalog/src/test/resources/application-test.yml:16`,
`order/.../application-test.yml:19`).

Additional facts:

- `config-server` runs as a single instance, backed by git (local filesystem,
  `file://${user.home}/IdeaProjects/config-repo`) + Vault, in that dual order — but since
  nothing imports from it, this backend is effectively dormant.
- Neither `config-server` nor `eureka-server` appears in `docker-compose.yml` at all —
  both run as standalone processes outside the orchestrated stack, with no replica/HA
  config anywhere.
- No `spring.cloud.config.retry.*` or `fail-fast` settings exist repo-wide — consistent
  with config-server not being wired into the startup path, so there's nothing to retry.
- `eureka-server` is explicitly configured as a lone, non-peer-aware instance
  (`fetch-registry: false`, `register-with-eureka: false`, no peer `defaultZone` list) —
  and **is** actually depended on (gateway routes via `lb://`, business services register
  with it). This is the real single point of failure, already tracked as a High-severity
  finding in the prior review (unauthenticated Eureka/config-server, Top-10 #5).

### Assessment

There is currently no config-server bottleneck to prevent, because nothing depends on
it — Vault has silently superseded it. The actual risk is architectural drift: a module
exists, is documented in the project layout, and looks load-bearing, but isn't — anyone
reading `CLAUDE.md`'s project layout or the module list would reasonably assume it's in
the config-fetch path. That's a maintenance and onboarding hazard independent of "bottleneck."

Eureka, by contrast, genuinely is a single point of failure today, and is the piece that
actually matters for service communication (routing + discovery), including the `lb://`
fix recommended in Topic A.

**This is a scope decision, not an implementation detail** — per the ambiguity tiers in
the root `CLAUDE.md`, keeping vs. decommissioning a whole service module needs an explicit
answer before any plan is written. Three options:

1. **Decommission `config-server`.** Vault already owns config/secrets end-to-end; remove
   the now-dead module, its Docker image, and the commented-out bootstrap file. Simplest,
   matches YAGNI ("don't run infra nobody uses"), and eliminates the future-bottleneck
   risk by eliminating the component.
2. **Actually adopt `config-server`** for non-secret shared config (e.g. Resilience4j
   default thresholds, feature-flag toggles) while Vault stays the secrets store. Requires:
   client-side `fail-fast=false` + retry config, HA (2+ peer instances behind a load
   balancer, shared git backend), and basic-auth/mTLS between config-server and clients —
   folding into the already-tracked Eureka/config-server security gap.
3. **Leave as-is, fix Eureka HA only.** Accept config-server's dormancy for now (low risk
   since nothing depends on it), but make Eureka peer-aware (2+ instances, mutual
   `defaultZone` registration) since it's the piece actually in the request path, and add
   the missing authentication to both.

## Decisions

1. **config-server: decommission.** It is dormant (nothing imports it — Vault already
   owns config/secrets end-to-end). Remove the module, its Docker image/build, and the
   commented-out `gateway/src/main/resources/config-server-bootstrap.yml`. Matches YAGNI
   and removes the drift/onboarding hazard outright.
2. **Eureka: add HA, in docker-compose.** Bring `eureka-server` into the orchestrated
   stack as two peer-aware instances (mutual `defaultZone` registration,
   `register-with-eureka: true` / `fetch-registry: true` on each), added to
   `docker-compose.yml` alongside the existing services. Pair with the already-tracked
   authentication gap (Top-10 #5 in the prior review) so HA doesn't ship unauthenticated.
3. **gRPC: no adoption, no flagged future pilot.** No known workload currently needs
   gRPC-level throughput/streaming. Revisit only if a concrete high-throughput/streaming
   use case materializes.

## Next steps

Fold the above into an implementation plan covering, in priority order:

1. `config-server` module removal (Maven module, Docker build/image, dead bootstrap
   config, any lingering docs/references).
2. `eureka-server` HA: second peer instance + peer-aware config on both, added to
   `docker-compose.yml`.
3. Eureka authentication (closes the already-tracked security gap now that HA makes it a
   two-instance concern).
4. `order`'s `lb://` migration for its profile/catalog/payment calls (replace hardcoded
   hosts with Eureka-resolved load-balanced clients).
5. `StructuredTaskScope` fan-out fix for `order`'s sequential profile/catalog/payment
   calls.
