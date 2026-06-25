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

## Known migration gaps
- **Pre-Boot-4 — does NOT compile yet** (~112 migration hits as of 2026-06-23):
  `javax.*` → `jakarta.*`, `@EnableEurekaClient` removal, pre-lambda Security DSL →
  lambda + `@EnableMethodSecurity`, `ResponseEntity.getStatusCodeValue()` →
  `getStatusCode().value()`. Migrate toward root conventions when you touch this code;
  never copy the legacy pattern forward. Build standalone with
  `./mvnw -f payment/pom.xml ...`.
