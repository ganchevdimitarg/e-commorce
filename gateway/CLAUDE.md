# CLAUDE.md — gateway-service

> `gateway` is the **WebFlux edge** (Spring Cloud Gateway) for the
> `com.ganchevdimitarg:e-commerce` Spring Boot 4.1.0 / Java 25 monorepo. Package
> `com.ganchevdimitarg.gateway`. Port **8081**. Owns routing, OAuth2 login and
> rate-limiting; downstream services trust the headers it injects.

Shared conventions, stack rules, hooks, skills and pattern docs live at the **repo
root**: `../CLAUDE.md` and `../docs/context/`. This file records only what is specific
to this module — read the root file first.

## Module specifics
- Role: API gateway / edge — the **only** module where reactive types (`Mono`/`Flux`,
  WebFlux) are the norm. Uses `spring-cloud-starter-gateway-server-webflux` and
  `spring-boot-starter-oauth2-client`.
- Datastore: none — stateless edge.
- Auth: validates the OAuth2 session/JWT and injects `X-User-Id` / `X-User-Roles` for
  downstream services. Owns auth + rate-limit per the root architecture rules.

## Known migration gaps
- Migrated to Boot 4.1.0 / Java 25 (build green as of 2026-06-23). The root reactor
  build cannot run until the pre-Boot-4 modules catch up — build standalone with
  `./mvnw -f gateway/pom.xml ...`.
