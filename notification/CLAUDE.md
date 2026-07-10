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
- Compiles green on Boot 4.1.0 / Java 25 (verified 2026-07-10); the module's earlier
  compilation issues have been resolved. Still lagging conventions: minimal test suite,
  legacy springdoc 1.x/jakarta.mail deps, no Dockerfile - lift toward root conventions
  when touching this module (full parity is a tracked follow-on plan).
