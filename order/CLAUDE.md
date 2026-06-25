# CLAUDE.md — order-service

> `order` owns **order management** in the `com.ganchevdimitarg:e-commerce` Spring Boot
> 4.1.0 / Java 25 monorepo. Package `com.ganchevdimitarg.order`. Port **8086**. Backed
> by **PostgreSQL**.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: order lifecycle and persistence; OAuth2 resource server (trusts gateway headers).
- Datastore: PostgreSQL (own schema, Flyway-managed). No cross-service joins.
- Web stack: WebMVC for business endpoints.

## Known migration gaps
- **Pre-Boot-4 — does NOT compile yet** (~106 migration hits as of 2026-06-23):
  `javax.*` → `jakarta.*`, `@EnableEurekaClient` removal, pre-lambda Security DSL →
  lambda + `@EnableMethodSecurity`, `ResponseEntity.getStatusCodeValue()` →
  `getStatusCode().value()`. Migrate toward root conventions when you touch this code;
  never copy the legacy pattern forward. Build standalone with
  `./mvnw -f order/pom.xml ...`.
