# ADR-006: Scoped, replayable idempotency keys and the Redis-outage policy

**Date:** 2026-10-20  
**Status:** Accepted

## Context

The pre-Phase-4 implementation used a one-way latch: `IdempotencyInterceptor.preHandle`
wrote `"1"` under `catalog:idempotency:<client-key>` with a 24 h TTL and returned 409 on
every repeat. Three defects followed from that design:

1. **Keys were global.** Two principals (or two endpoints) that happened to use the same
   `Idempotency-Key` collided, so one request could be rejected because of another
   client's traffic.
2. **A failed request burned its key.** A 5xx — or any unhandled exception — left the
   key in Redis, so the retry that should fix the failure was rejected for 24 hours.
3. **No response replay.** A genuine retry could not receive the original result; it
   only received a 409 with no information about what had happened.

TODO 4.1 requires principal + method + path scoping, an `IN_PROGRESS` / `COMPLETED`
state machine, release on 5xx, replay of the stored response, and an explicit
Redis-outage policy. The user decided on 2026-10-20 that the policy is **fail-open**.

## Decision

### Key formula

```
catalog:idempotency: + lowercase hex SHA-256(principal + "\n" + method + "\n" + requestURI + "\n" + clientKey)
```

- `principal` is `Authentication.getName()` — the JWT `sub` — or `anonymous` when the
  request is unauthenticated (`Authentication` null, not authenticated, or an
  `AnonymousAuthenticationToken`).
- The suffix is hashed so the key length is bounded and neither the user identifier nor
  the client key leaks into Redis key listings.
- The client key is supplied in the `Idempotency-Key` header and remains mandatory for
  guarded requests; `POST …:batch-get` is a read and stays unguarded.

### State machine

Every entry is a JSON `IdempotencyRecord(state, status, contentType, location, body)`.

| Stored state | Duplicate request | Action |
|---|---|---|
| `IN_PROGRESS` | Concurrent duplicate | 409 `application/problem+json` — the first request is still running |
| `COMPLETED` | Retry | Replay the stored status, `Content-Type`, `Location` and body; the handler does not run |
| missing (`null`) | Race with expiry | 409 — never execute an ambiguous request twice |
| unparseable (legacy `"1"`, corruption) | Rolling deploy or corruption | 409 — never replay or re-execute an ambiguous request |

- A new key is claimed with `SET NX` as `IN_PROGRESS` with a 24 h TTL.
- On a 2xx/3xx/4xx outcome the entry is replaced by a `COMPLETED` record with the same
  24 h TTL, so retries receive the original response — including a cached problem+json
  validation or conflict response.
- On a 5xx status or an exception escaping the handler the key is **deleted**, so the
  retry can execute and produce a fresh result.
- A failure to write the `COMPLETED` record is logged and swallowed. The response is
  already correct; the key stays `IN_PROGRESS` until the TTL expires.

### Redis-outage policy: fail-open

If Redis is unreachable, `IdempotencyStore` raises `IdempotencyUnavailableException`.
When that happens during `claim`, the interceptor logs at WARN and lets the request
proceed without protection. A release/complete failure after the response is written is
logged and swallowed; post-response bookkeeping must never change the outcome.

Rejected alternative: fail-closed (503). It would couple every catalogue write to Redis
availability, contradicting TODO 4.3's "cache failures must not fail requests" and
turning a cache outage into a full write outage.

### Response capture

`IdempotencyResponseCaptureFilter` (ordered `LOWEST_PRECEDENCE`, after Spring Security)
wraps guarded requests in a `ContentCachingResponseWrapper`, so `afterCompletion` can
read the committed body. `copyBodyToResponse()` in a `finally` block keeps ordinary
responses intact. Idempotency remains an MVC interceptor rather than a filter because
`BusinessException` subclasses must still be mapped to problem+json by
`@ControllerAdvice`; a filter would surface them as raw 500s.

## Consequences

- **Duplicates are possible during a Redis outage.** Two identical requests may both
  execute while Redis is down. Catalogue writes are low-value and recoverable, and the
  outage is logged, so this is an accepted trade-off.
- **A completion-storage failure leaves the key `IN_PROGRESS` until the TTL expires**
  (24 h). Retries of that request return 409 even though the original succeeded; the
  client must treat 409 as "outcome unknown, do not retry blindly" for the TTL window.
- **Legacy `"1"` keys are unparseable and yield 409 until they expire.** This is
  deliberate during a rolling deploy: an ambiguous request is never re-executed.
- **Cached 4xx responses are replayed.** A client that fixes its payload and retries with
  the same key receives the cached validation error until the key expires; it must use a
  new key for a materially different request.
- **In-progress entries are never automatically cleared by a crashed request.** A process
  crash between claim and completion leaves the key `IN_PROGRESS` until the TTL. A
  shorter claim TTL or a heartbeat would address this, but is out of scope for Phase 4.
- The key formula changes the Redis key space; old latch keys (`catalog:idempotency:<raw
  client key>`) are simply orphaned and expire on their own TTL.
