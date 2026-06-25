# CLAUDE.md — notification-service

> `notification` sends **customer notifications** (email/SMTP) in the
> `com.ganchevdimitarg:e-commerce` Spring Boot 4.1.0 / Java 25 monorepo. Package
> `com.ganchevdimitarg.notification`. Port **8085**. Backed by **PostgreSQL**.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: notification dispatch (email via SMTP, port 587); OAuth2 resource server.
- Datastore: PostgreSQL (own schema, Flyway-managed). No cross-service joins.
- Web stack: WebMVC for business endpoints.

## Known migration gaps
- **Pre-Boot-4 — does NOT compile yet** (~42 migration hits as of 2026-06-23):
  `javax.*` → `jakarta.*`, `@EnableEurekaClient` removal, pre-lambda Security DSL →
  lambda + `@EnableMethodSecurity`, `ResponseEntity.getStatusCodeValue()` →
  `getStatusCode().value()`. Migrate toward root conventions when you touch this code;
  never copy the legacy pattern forward. Build standalone with
  `./mvnw -f notification/pom.xml ...`.
