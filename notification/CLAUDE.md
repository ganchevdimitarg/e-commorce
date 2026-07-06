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

## Migration status
- **Boot-4 / Java-25 migrated (2026-07-06)** — builds green standalone
  (`./mvnw -f notification/pom.xml clean test -Dtest='*Test,*IT'`, 15 tests). Email dispatch,
  problem+json errors, Bean Validation, method security, audit columns/soft-delete, typed Kafka
  consumer with DLT, and Redis idempotency are all in place with Testcontainers coverage.
- Legacy bootstrap.yml was replaced by `spring.config.import` (Vault import lives in
  `application-dev.yml`; the test profile never contacts Vault). Kafka needs `@EnableKafka` and
  `spring-boot-starter-flyway` (not bare `flyway-core`) under Boot 4.

## Open follow-ups
- Inbound topic renamed `sentMail` → `notification.email.requested` (convention): **producers
  must publish to the new topic** — coordinate with the sending service(s).
- Idempotency guard suppresses duplicate sends but a failed first attempt releases the key; a
  send that fails *after* Redis write but the JVM dies would not re-send. Acceptable per repo norms.
