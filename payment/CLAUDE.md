# CLAUDE.md — payment-service

> `payment` handles **payment processing** in the `com.ganchevdimitarg:e-commerce`
> Spring Boot 4.1.0 / Java 25 monorepo. Package `com.ganchevdimitarg.payment`. Port
> **8087**. Backed by **PostgreSQL**.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: payment processing; OAuth2 resource server (trusts gateway headers).
- Datastore: PostgreSQL (own schema, Flyway-managed). Balances/amounts must stay
  strongly consistent — do not cache them (see root caching rules).
- Web stack: WebMVC for business endpoints.

## Migration status (verified 2026-07-08)
- **Boot 4.1.0 / Java 25 — migrated and remediated.** `./mvnw -f payment/pom.xml clean
  verify` is green (18 test files, JaCoCo gate ≥ 80%). Grade-A remediation landed
  2026-07: full-refund endpoint, Stripe calls (charge/refund/delete) outside DB
  transactions with idempotency keys, provider-first customer/card creation, method
  security, problem+json envelope. See `docs/superpowers/plans/2026-07-07-payment-grade-a-remediation.md`
  and `decisions.md` (2026-07-07/08).
- Deferred follow-ups: no Dockerfile yet; Kafka saga events (roadmap Phase 3) will
  replace the synchronous charge/refund API consumed by `order`.
