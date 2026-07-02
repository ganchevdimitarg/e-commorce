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
- Outbound HTTP: blocking `RestClient` (OAuth2 client-credentials) + Spring Cloud
  `CircuitBreakerFactory`. No WebFlux/WebClient — reactive belongs only to `gateway`.

## Configuration & profiles
- `application.yml` holds profile-independent defaults (app name, actuator health probes).
- `application-dev.yml` holds environment config; `dev` is auto-included at startup via
  `bootstrap.yml` (`spring.profiles.include: dev`). Add `application-<env>.yml` and run with
  `--spring.profiles.active=<env>` for other environments.
- `bootstrap.yml` also wires Vault config — a full `@SpringBootTest` therefore needs
  Vault/config-server/eureka reachable; service logic is covered by context-free unit tests
  (see `src/test`), a full Testcontainers IT is not yet wired.

## Known gaps (verified 2026-07-02)
The Java source **already compiles on Boot 4.1.0** — `./mvnw -f order/pom.xml clean compile`
is green. The earlier `javax.*` / `@EnableEurekaClient` / pre-lambda-Security /
`getStatusCodeValue()` claims were stale: source is already `jakarta.*` with a lambda
`SecurityFilterChain`. Real remaining gaps, in priority order:

- **Runtime OpenAPI dep** — `pom.xml` pulls `springdoc-openapi-ui 1.6.13` (the Boot-2 line),
  a context-startup risk on Boot 4. The code uses only `io.swagger.v3.oas.annotations.*`
  (version-agnostic), so swap the dep for `springdoc-openapi-starter-webmvc-ui` (2.x). The
  springfox `<properties>` in the pom are dead — remove them.
- **Reactive stack in a WebMVC service** — `WebClientConfig`, `OrderServiceImpl`,
  `ChargeServiceImpl` use `WebClient`/`Mono` (pulls `spring-boot-starter-webflux`). Convention
  is WebMVC-only outside `gateway`: migrate to `RestClient` and drop the webflux deps.
- **Stray deps** — `gson` (project standard is Jackson) and `spring-boot-devtools` should go.
- **No tests** — `src/test` does not exist; the Stop `verify-gate` hook + 80% coverage gate
  currently cannot pass.
- **No audit columns** — `V1` uses `created_on TIMESTAMP`; missing `created_at`/`updated_at`/
  `deleted_at TIMESTAMPTZ`. Add a `V2` migration (never edit `V1`).
- **No Dockerfile** — required by root conventions (multi-stage, non-root, HEALTHCHECK).

Migrate toward root conventions when you touch this code; never copy the legacy pattern
forward. Build standalone: `./mvnw -f order/pom.xml ...` (root reactor is blocked).
