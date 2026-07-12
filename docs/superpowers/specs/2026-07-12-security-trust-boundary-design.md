# Design: Security & Trust Boundary Hardening (Workstream A)

**Date:** 2026-07-12
**Origin:** `docs/reviews/2026-07-12-production-readiness-review.md`, Categories 13 (Security) and
14 (Authentication & authorization), currently 5/10 and 7/10. This is workstream A of an 8-workstream
roadmap to raise every review category to 9+/10 (roadmap and sequencing recorded in the session's
plan; workstreams B–H are out of scope here and will each get their own design session).

## Problem

`payment`'s `ChargeController` (`payment/src/main/java/com/ganchevdimitarg/payment/controller/ChargeController.java:25,33`)
accepts an `X-Request-Header("X-User-Id")` and passes it directly into
`ChargeServiceImpl.createCharge`/`refund` (`ChargeServiceImpl.java:60-65`) with no cross-check against
the authenticated principal. `gateway`'s `UserIdentityGlobalFilter`
(`gateway/src/main/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilter.java:36-50`)
correctly strips and re-injects this header from the validated OAuth2 principal at the edge — but
nothing stops a caller from reaching a downstream service directly (bypassing gateway) with a validly
`payment.write`-scoped token and an arbitrary `X-User-Id`, charging or refunding any customer. This is
a live financial-fraud vector, not a theoretical one, since `payment` processes real transactions via
Stripe.

Three smaller gaps ride along in the same categories: `profile` places `@PreAuthorize` on controller
methods instead of the service layer (`ProfileController.java:81,106`); `notification` NPEs (500)
instead of returning 401 on a missing `Authorization` header
(`ResourceServerConfig.java:64-75`); and a BCrypt hash of a since-compromised gateway client secret
still lives in `authentication`'s migration history
(`V2__insert_data.sql:1-2`, rotated by `V8__rotate_gateway_client_secret.sql` but not purged from
history, since committed migrations are never edited per this repo's Flyway convention).

## Architecture: shared-secret HMAC trust chain

**Rejected alternatives:**
- **mTLS between gateway and services** — a stronger guarantee (covers the whole connection, not just
  these headers) but requires certificate issuance/rotation/trust-store management with no existing
  cert infrastructure in this repo. Rejected as disproportionate to the actual gap.
- **Network-level isolation only** (rely on Docker/Eureka segmentation so only gateway can reach
  downstream services) — cheapest, but doesn't defend against a compromised service on the same
  network, and there's no evidence any network policy enforces this today. Rejected as too weak on its
  own.

**Chosen: shared-secret HMAC header**, because it reuses the Vault-based secret convention already
wired into 7/9 modules, requires no new infrastructure, and directly closes the specific gap (headers
being trusted with no proof of gateway origin) without over-engineering a broader zero-trust network.

### Components

**1. `gateway` — signing (new logic in existing filter)**

`UserIdentityGlobalFilter` (`gateway/src/main/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilter.java`)
gains a step after it establishes the validated `X-User-Id`/`X-User-Roles` values: compute
`X-Gateway-Timestamp` (epoch millis, now) and `X-Gateway-Signature` = HMAC-SHA256 over the string
`X-User-Id + "|" + X-User-Roles + "|" + X-Gateway-Timestamp`, keyed by a secret read from Vault. All
three headers are injected into the outbound request.

**2. `client` module — verification (new shared component)**

A new filter, e.g. `com.ganchevdimitarg.client.security.GatewayTrustFilter`, added to the `client`
shared library (already consumed by `catalog`, `order`, `payment`, `notification`, `profile`).
Recomputes the same HMAC over the three inbound header values using the shared secret; rejects with
`401` (via the service's existing `BusinessException`/`ProblemDetail` convention) if:
- any of the three headers is missing,
- the signature doesn't match,
- `X-Gateway-Timestamp` is more than 30 seconds old (replay window — generous enough to cover a
  `gateway → order → payment` hop within one request lifecycle, tight enough to reject replay).

Each of the five consuming services registers this filter in its existing `SecurityFilterChain`/
`ResourceServerConfig`, ahead of the point where `X-User-Id` is currently read.

`gateway` and `authentication` don't depend on `client` today (verified: neither declares it as a
dependency in `pom.xml`) — `gateway` only needs the signing half (added directly, no shared-module
change), and `authentication` doesn't rely on `X-User-Id` trust for its own endpoints (it's the
identity source of truth, not a consumer), so it needs neither half.

**3. Secret provisioning**

New Vault-backed secret (path convention matching existing usage, e.g.
`secret/e-commerce/gateway-trust`), injected as `${GATEWAY_TRUST_SECRET}` into `gateway` and into each
of the five consuming services' `application.yml`, following the existing `${ENV_VAR}` convention
(no plaintext anywhere).

**4. Service-to-service propagation**

`order` calls `payment`'s charge/refund endpoints directly and synchronously today (per
`payment/CLAUDE.md`, pending a future Kafka-saga replacement). `order`'s outbound HTTP client for that
call must **forward** the three inbound headers unchanged rather than regenerating them — the
signature authenticates the original gateway-issued claim, not the immediate caller, so it must travel
with the request end-to-end. This is the only cross-service change beyond the five independent
per-service filter registrations.

### Data flow

```
Browser/client → gateway
  gateway validates OAuth2 session/JWT
  gateway computes X-User-Id, X-User-Roles, X-Gateway-Timestamp, X-Gateway-Signature
  gateway → catalog/order/payment/notification/profile (headers attached)
    GatewayTrustFilter (client module) verifies signature + freshness
    → 401 if invalid/missing/stale
    → request proceeds, X-User-Id now a verified claim
  order → payment (direct call)
    order forwards the same 3 headers unchanged (no re-signing)
    payment's GatewayTrustFilter verifies the original gateway signature — still valid within 30s
```

### Error handling

`GatewayTrustFilter` returns the same `problem+json` shape each service already produces for 401s
(via each module's existing `BusinessException`/`ControllerExceptionHandler` — see the review's
Category 11 for which modules already have this correctly wired; `gateway` and `profile`'s gaps there
are out of scope for this workstream and tracked separately under workstream F). A missing/invalid
signature is treated as an authentication failure (401), not an authorization failure (403) — the
caller's identity claim itself couldn't be verified.

### Folded-in fixes (no new design — already fully specified in the review)

- `profile`: move `@PreAuthorize` from `ProfileController.java:81,106` to `ProfileService`.
- `notification`: null-check the `Authorization` header in `ResourceServerConfig.java:64-75` before
  `.replace(...)`/`decode(...)`; return 401 instead of NPEing to 500.
- `authentication`: document the burned/rotated secret incident in `decisions.md` — deferred until
  the currently-staged doc deletions (unrelated, user-owned issue) are resolved.
- `gateway`: no CORS policy added. No evidence of a browser-based frontend calling gateway
  cross-origin today (only same-origin swagger-ui) — documented as a deliberate "not needed yet,
  revisit if a browser frontend is added" decision.
- Once the HMAC mechanism lands, no `payment`-specific code change is needed for the `X-User-Id` gap —
  it closes at the platform level because the header becomes a verified claim.

## Testing

- Unit tests for the HMAC sign/verify logic (`client` module): valid signature, expired timestamp,
  tampered signature, missing header — each asserting the correct accept/401 outcome. This is
  `client`'s first real test coverage, a down payment on workstream B (Testing).
- Integration test: a direct request to `catalog` (or `payment`) carrying a forged `X-User-Id` with no
  valid `X-Gateway-Signature` is rejected with 401.
- Integration test: the `order → payment` call chain, exercised end-to-end, confirms the forwarded
  headers are accepted by `payment`'s filter (proves propagation works, not just per-service
  verification in isolation).
- Unit tests for `profile`'s `@PreAuthorize` relocation (confirm the check still fires from the
  service layer) and `notification`'s null-check (missing header → 401, not 500).

## Scope boundary

Out of scope for this workstream (tracked in the roadmap under their own letters): coverage
enforcement (B), idempotency/concurrency (C), observability rollout (D), JPA/soft-delete fixes (E),
gateway/profile exception-handling gaps beyond what's needed for this workstream's own 401 responses
(F), config-hygiene cleanup (G), documentation restoration (H).
