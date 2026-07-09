# Notification Service — Boot 4 Migration & Remediation Design

**Date:** 2026-07-05
**Module:** `notification` (port 8085, PostgreSQL, WebMVC, OAuth2 resource server)
**Status:** Approved scope — full migration + remediation ("all of this")
**Reference module:** `order` (canonical Boot-4 / Java-25 migrated service)

## Why

The notification service is one of the repo's flagged pre-Boot-4 lagging modules. A code
review found it is not merely lagging — its **core function is dead**: email dispatch is
commented out, the `EmailService` bean is unwired (so the context cannot start), and the
one timing aspect is thread-unsafe on virtual threads. On top of that it violates numerous
root `CLAUDE.md` conventions (no tests, no problem+json, no audit columns, off-pattern
Kafka, redundant custom validation, WebFlux on a WebMVC service).

This design brings the module up to the same standard as `order`, restores the email
feature, and removes the accumulated dead/legacy code. We mirror `order`'s patterns
verbatim wherever they apply rather than inventing new ones.

## Goals

- Service boots, compiles standalone (`./mvnw -f notification/pom.xml clean verify`), sends email.
- Full compliance with root `CLAUDE.md` (exceptions, data model, Kafka, security, testing, Docker).
- Coverage gate met: 80% line, 100% on the domain model.
- No dead/commented code, no legacy dependencies.

## Non-goals

- Changing the email provider (stays Gmail SMTP 587).
- New notification channels (SMS/push) — out of scope.
- Changing the `client` shared module or gateway.

---

## Architecture (target)

Layering stays `controller → service → dao → mapper`, matching `order`. The dead centre is
restored and the redundant AOP validation layer is deleted in favour of Bean Validation.

```
Kafka "notification.email.requested" ─┐
                                      ├─► EmailService.sendSimpleMail ─► JavaMailSender (SMTP)
POST /api/v1/notification/send-email ─┘                              └─► NotificationService.createNotification ─► Postgres
                                                                                (audit columns, soft-delete)
DLT: notification.email.requested.DLT  ◄── DefaultErrorHandler (retry then dead-letter)
```

Error responses: `application/problem+json` via `ProblemDetail` (RFC 9457), identical to
`order`'s `ControllerExceptionHandler`.

---

## Work, grouped into phases

Phases are ordered so the service is **bootable and functional after Phase 2**, then
progressively hardened. Each phase ends green (`clean verify`).

### Phase 0 — Build on Boot 4 (make it compile)
- `notification/pom.xml`: drop `spring-boot-starter-webflux`, `springfox-*`, `h2`,
  `springfox-swagger-ui`; replace `springdoc-openapi-ui` with
  `springdoc-openapi-starter-webmvc-ui` (`3.0.3`, mirror `order`); add
  `spring-boot-starter-validation`, `spring-boot-testcontainers`,
  `testcontainers-junit-jupiter`, `testcontainers-postgresql`; add GreenMail
  (`com.icegreen:greenmail-junit5`) for SMTP tests. Collapse the duplicate MapStruct
  version properties to one.
- Remove Netflix Eureka legacy annotations if present; confirm `@EnableEurekaClient` gone.
- Rename package `excaption` → `exception`.

### Phase 1 — Restore email + fix wiring (make it work)
- `EmailServiceImpl implements EmailService`; uncomment/restore `JavaMailSender`,
  `sendSimpleMail`, `sendMailWithAttachment`. Inject `JavaMailSender`.
- `sendSimpleMail` persists via `NotificationService.createNotification` after a successful
  send; wrap `MailException` → a domain exception (not raw).
- Delete the commented code blocks.

### Phase 2 — Correctness & security fixes (blockers)
- `MeasureAspect`: replace the shared `StopWatch` field with a per-invocation local
  (`@Around` measuring `System.nanoTime()` around `proceed()`), thread-safe on virtual threads.
- Security: single consistent scope model. Endpoint `@PreAuthorize` and the filter-chain
  matcher must agree. Adopt `order`'s `ResourceServerConfig`: add `@EnableMethodSecurity`,
  `ProblemAuthenticationEntryPoint` + `ProblemAccessDeniedHandler`. Restrict `/actuator/**`
  to `/actuator/health` public; the rest authenticated. Add `@PreAuthorize` to the
  attachment endpoint (or remove the endpoint if not needed — decide in plan).

### Phase 3 — Exceptions → problem+json
- Introduce `BusinessException`, `NotFoundException`, `ConflictException`,
  `ValidationException` (copy `order`'s hierarchy, `notification` package).
- Replace `ControllerExceptionHandler` + `ErrorMessage` with `order`'s `ProblemDetail`
  version (`RestControllerAdvice extends ResponseEntityExceptionHandler`). Delete
  `ErrorMessage` (a `@Data` class).

### Phase 4 — Validation via Bean Validation
- Add constraints to `NotificationDto` record components (`@NotBlank`, `@Email` on
  recipient, `@Size` on subject/msgBody). Cross-field rules in the compact constructor →
  `ValidationException`.
- `@Valid` on controller `@RequestBody`.
- **Delete** the custom validation stack: `@ValidationRequest`, `ValidationRequestDtoAspect`,
  `ValidateRequest`, `ValidateRequestImpl`, `InvalidRequestDataException`.

### Phase 5 — Data model & persistence
- Flyway `V2__notification_audit_columns.sql`: rename `created_on` → `created_at`; add
  `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `deleted_at TIMESTAMPTZ NULL`; set
  `created_at NOT NULL DEFAULT now()`; add `updated_at` trigger (per database-patterns.md).
- `Notification` entity: audit fields, `@Getter/@Setter/@NoArgsConstructor` (+`@Builder`
  allowed), drop `@AllArgsConstructor` if it invites misuse; align field names.
- `@Transactional` on `NotificationServiceImpl.createNotification`.
- Soft-delete query pattern in `NotificationDao` (`findActiveById` filtering `deleted_at IS NULL`).

### Phase 6 — Kafka hardening
- Topic constant `NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested"`; consumer
  group `notification-group`; DLT `notification.email.requested.DLT`.
- Typed `JsonDeserializer<NotificationDto>` (or shared `ObjectMapper` bean — no
  `new ObjectMapper()`); `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`
  (retry then dead-letter), mirroring root `kafka.md`.
- `KafkaListenerService` consumes the typed record; no manual JSON parsing.
- Idempotency: guard duplicate sends (Redis `idempotency:notification:<key>` or a dedupe key
  on the event) so a redelivery does not double-send. **Scope decision flagged for the plan.**

### Phase 7 — Tests (Testcontainers + GreenMail)
- `AbstractIntegrationTest` (copy `order`'s: Postgres `@ServiceConnection`, `@ActiveProfiles("test")`).
- Add Kafka container to the base for the listener IT.
- Unit tests: `EmailServiceImpl` (GreenMail), `MeasureAspect`, `NotificationServiceImpl`,
  DTO validation, exception handler. Integration: controller (MockMvc + security),
  Kafka listener → email → persistence (Awaitility, no `Thread.sleep`).
- Meet 80% line / 100% domain coverage.

### Phase 8 — Docker & ops
- `notification/Dockerfile`: copy `order`'s multi-stage (temurin 25-jdk → 25-jre, non-root,
  `curl` HEALTHCHECK on `/actuator/health`, `-pl notification -am`, explicit `notification.jar`,
  EXPOSE 8085).
- `.run/notification.xml` IntelliJ run config.

---

## Error handling & data flow

- REST validation failure → `ValidationException` / `ConstraintViolationException` → 400 problem+json.
- Auth failure → `ProblemAuthenticationEntryPoint` (401) / `ProblemAccessDeniedHandler` (403), problem+json.
- SMTP failure → domain exception → 500 problem+json (REST) or retry→DLT (Kafka path).
- Successful send → persist `Notification` row, return confirmation string (or a small
  response record — decide in plan).

## Testing strategy

Testcontainers (real Postgres + Kafka; never H2/embedded), GreenMail for SMTP, MockMvc +
`spring-security-test` for authz, Awaitility for async assertions. Naming
`should_<behavior>_when_<condition>`.

## Resolved scope decisions (2026-07-05)

1. **Idempotency store** — **Add Redis + `Idempotency-Key`** (`idempotency:notification:<key>`,
   24h TTL). Adds `spring-boot-starter-data-redis` + Testcontainers Redis to this module,
   mirroring `order`. Guards both the REST and Kafka paths.
2. **Attachment endpoint** — **Remove** `sendMailWithAttachment` (endpoint + service method).
   It is a no-op; delete rather than carry dead code.
3. **Send response shape** — **`NotificationResponse` record** (`id, recipient, status, sentAt`),
   `NON_NULL` JSON, replacing the raw `String` return.

These fold into the phases above: Phase 0 adds the Redis + Testcontainers-Redis deps;
Phase 1 returns `NotificationResponse` and drops the attachment path; Phase 6 uses Redis for
the idempotency guard; Phase 7 adds Redis to `AbstractIntegrationTest`.

## Verification (per phase, do not stop until green)

```bash
./mvnw -f notification/pom.xml clean verify
./mvnw -f notification/pom.xml checkstyle:check
./mvnw -f notification/pom.xml flyway:validate
```
