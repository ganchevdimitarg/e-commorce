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
- Compiles on Boot 4.1.0 / Java 25 (verified 2026-07-08); full remediation is in
  progress on `feat/notification-boot4-migration`. Still open on `main`: no tests, no
  audit columns, no MDC filter, no `@RetryableTopic`/DLT on the Kafka listener (deferred
  to that branch — see the note in `KafkaListenerService`), `excaption` package typo.
  Migrate toward root conventions when you touch this code; never copy the legacy
  pattern forward. Build standalone with `./mvnw -f notification/pom.xml ...`.
