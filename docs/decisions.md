# Decision log — e-commerce monorepo

Running log of cross-module architectural decisions. Read before proposing
architectural changes (per root `CLAUDE.md`). Module-local decisions live in
`<module>/decisions.md` (currently: `gateway/`, `order/`, `payment/`); formal records in
`docs/adr/` (ADR-001 WebMVC over WebFlux, ADR-002 Avro/Schema Registry, ADR-003
records-first, ADR-004 choreography sagas).

Format: `- [YYYY-MM-DD] <decision> — <why>`

## Decisions

- [2026-07-08] `main` fast-forwarded to `feat/order-boot4-migration` (63 commits) — the
  remediated gateway/order/payment code had diverged from `main`, which no longer
  compiled; keeping two truths was the repo's largest standing risk.
- [2026-07-08] Swagger-UI OAuth `client-secret` is `${GATEWAY_CLIENT_SECRET}` in every
  resource-server config — the literal `secret` value was committed in four modules.
- [2026-07-06] Notification DLT/`@RetryableTopic` deferred to
  `feat/notification-boot4-migration` — coordinated change tracked in
  `order/decisions.md`; topic renamed to `order.notification.requested`, group to
  `notification-group` ahead of it.
- [2026-07-05] Gateway owns the identity contract: `UserIdentityGlobalFilter` strips
  inbound `X-User-*` headers and injects trusted `X-User-Id`/`X-User-Roles` from the
  OAuth2 principal — downstream services must not re-introspect for identity.
- [2026-07-05] Payment provider calls (Stripe) happen **outside** DB transactions; local
  rows are written by separate `@Transactional` persistence beans; Stripe idempotency
  keys are threaded controller→service→gateway. See `payment/decisions.md`.
- [2026-07-05] Vendor SDKs are confined behind one port per service (`PaymentGateway` /
  `StripePaymentGateway`) — no `com.stripe.*` types beyond that class. Apply the same
  rule to future vendor integrations; do not generalise into hexagonal ceremony.
- [2026-06-23] Per-module `CLAUDE.md` files are thin deltas over the root file —
  identity + module-specific deviations only.
- [2026-06-21] WebMVC + virtual threads for business services; WebFlux only in
  `gateway` (ADR-001). `profile` currently deviates (reactive end-to-end) — align on
  next rework, do not extend.
- [2026-06-21] Kafka events are JSON today; Avro + Schema Registry via a future
  `common-events` module (ADR-002) — do not add per-module Avro in the meantime.
