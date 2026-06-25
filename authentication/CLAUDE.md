# CLAUDE.md — authentication-service

> `authentication` is the OAuth2 **authorization server** (issues and introspects
> tokens, owns user credentials) in the `com.ganchevdimitarg:e-commerce` Spring Boot
> 4.1.0 / Java 25 monorepo. Package `com.ganchevdimitarg.auth`. Port **8082**.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: OAuth2 authorization server — token issuance/introspection and credential
  storage (`spring-boot-starter-oauth2-authorization-server`).
- Web stack: WebMVC. Not an edge service.
- Datastore: PostgreSQL (own schema, Flyway-managed); `spring-boot-starter-data-mongodb`
  is also on the classpath. No cross-service joins.
- Security: this service **is** the token issuer — it does not trust gateway-injected
  `X-User-Id` / `X-User-Roles` headers the way downstream resource servers do.

## Known migration gaps
- Migrated to Boot 4.1.0 / Java 25 (build green as of 2026-06-23). Keep it green:
  migrate any code you touch toward the root conventions; never reintroduce `javax.*`
  or pre-lambda Security DSL.
