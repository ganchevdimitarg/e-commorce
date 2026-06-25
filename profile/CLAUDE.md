# CLAUDE.md — profile-service

> `profile` owns **customer profiles** in the `com.ganchevdimitarg:e-commerce` Spring
> Boot 4.1.0 / Java 25 monorepo. Package `com.ganchevdimitarg.profile`. Port **8083**.
> Backed by **MongoDB**.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: customer profile read/write; OAuth2 resource server (trusts gateway headers).
- Datastore: **MongoDB** (the only Mongo-backed business service) — apply
  `../docs/context/mongodb-patterns.md`, not the JPA/Flyway rules.
- Web stack: WebMVC for business endpoints (`spring-boot-starter-webflux` is on the
  classpath, but reactive types stay out of business logic per root rules).

## Known migration gaps
- Migrated to Boot 4.1.0 / Java 25 (build green as of 2026-06-23).
- **Duplicate application class**: a legacy `com.concordeu.profile.ProfileApplication`
  still exists alongside `com.ganchevdimitarg.profile.ProfileApplication`. Remove the
  `com.concordeu.*` package when next touching this module.
