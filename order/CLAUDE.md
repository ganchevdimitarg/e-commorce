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
  Vault/eureka reachable; a Testcontainers IT (`OrderPersistenceIT`) now runs
  the full context in a `test` profile that disables Vault/eureka.

## Migration status (verified 2026-07-04)
The Boot-4 migration and grade-A remediation are **complete** — `./mvnw -f order/pom.xml
clean verify` is green (40 unit tests) and `OrderPersistenceIT` passes against a real
Postgres. Resolved since the earlier audit: OpenAPI on `springdoc-openapi-starter-webmvc-ui`
3.x; reactive stack replaced by `RestClient` (`WebClientConfig` gone); `gson`/devtools
removed; audit columns added (`V2`); unit + integration tests present; Dockerfile present;
`excaption` package renamed to `exception`. Three Boot-4 autoconfig gaps surfaced by the IT
were also closed: `spring-boot-starter-flyway` (migrations now run), `spring-boot-starter-oauth2-client`
(`ClientRegistrationRepository` now built), and dropping the Jackson-3-invalid
`write-dates-as-timestamps` property. See `decisions.md` (2026-07-04).

Deferred follow-ups (breaking, tracked separately): the `OrderDto.items : List<Item>`
entity leak → introduce `OrderLineDto(String productId, long quantity)` and map in the
service; the physical Kafka topic rename `"sentMail"` → `order.notification.requested`
(needs a coordinated change with the notification service).

Migrate toward root conventions when you touch this code; never copy the legacy pattern
forward. Build standalone: `./mvnw -f order/pom.xml ...` (root reactor is blocked).
