# Notification Service — Boot 4 Migration & Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the `notification` service to Boot-4/Java-25 parity with `order`, restore its dead email feature, and fix all convention violations found in review.

**Architecture:** WebMVC OAuth2 resource server on port 8085 backed by PostgreSQL. Email is sent via `JavaMailSender` (Gmail SMTP) triggered by both a REST endpoint and a Kafka listener; every send is persisted with audit columns and guarded by a Redis idempotency key. All patterns mirror the already-migrated `order` module verbatim.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Kafka, Spring Data JPA, Flyway, Redis (Lettuce), MapStruct, Testcontainers (Postgres + Kafka + Redis), GreenMail (SMTP), springdoc-openapi.

## Global Constraints

- Build standalone only: `./mvnw -f notification/pom.xml clean verify` — the root reactor is blocked until every module is on Boot 4.
- Package root: `com.ganchevdimitarg.notification`. Rename the misspelled `excaption` → `exception`.
- WebMVC only — no `Mono`/`Flux`/WebFlux dependency in this module.
- Records for all DTOs/commands/responses/events; JPA entities use `@Getter/@Setter/@NoArgsConstructor` (+`@Builder` allowed), never `@Data`.
- `@Transactional` on the service layer only; repositories return `Optional<T>`.
- All errors → `application/problem+json` (RFC 9457) via `ProblemDetail`; domain exceptions extend `BusinessException(HttpStatus, String code, String message)`.
- Every table has `created_at`, `updated_at`, `deleted_at TIMESTAMPTZ`; soft-delete via `deleted_at = now()`; `ddl-auto=validate`; Flyway owns the schema — never edit `V1`.
- Kafka: topic `notification.email.requested`, group `notification-group`, DLT `notification.email.requested.DLT`; no `new ObjectMapper()`.
- Redis: `GenericJackson2JsonRedisSerializer`, key `notification:<entity>:<id>`, TTL always set; idempotency key `idempotency:notification:<key>` 24h TTL.
- Tests: Testcontainers only (never H2/embedded); naming `should_<behavior>_when_<condition>`; no `Thread.sleep` (Awaitility); coverage 80% line / 100% domain.
- Docker: multi-stage `eclipse-temurin:25-jdk`→`:25-jre`, non-root, HEALTHCHECK, explicit `notification.jar`, EXPOSE 8085.
- British English in prose/comments; Conventional Commits; `./mvnw` wrapper always.

---

### Task 1: Fix the build — dependencies & package rename

**Files:**
- Modify: `notification/pom.xml`
- Rename: `notification/src/main/java/com/ganchevdimitarg/notification/excaption/` → `.../exception/` (and update all imports)

**Interfaces:**
- Produces: a compiling, Boot-4 dependency set; package `com.ganchevdimitarg.notification.exception`.

- [ ] **Step 1: Edit `pom.xml` dependencies.** Remove `spring-boot-starter-webflux`, `springfox-boot-starter`/`springfox-swagger-ui` (delete the properties too), the `h2` dependency, and `springdoc-openapi-ui`. Add:

```xml
<!-- OpenAPI (WebMVC) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc-openapi.version}</version>
</dependency>
<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<!-- Redis (idempotency) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<!-- Testcontainers -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<!-- SMTP test double -->
<dependency>
    <groupId>com.icegreen</groupId>
    <artifactId>greenmail-junit5</artifactId>
    <version>${greenmail.version}</version>
    <scope>test</scope>
</dependency>
```

Set `<springdoc-openapi.version>3.0.3</springdoc-openapi.version>` and `<greenmail.version>2.1.0</greenmail.version>` in `<properties>`; collapse the two MapStruct version properties (`org.mapstruct.version`, `mapstruct.version`) into a single `${mapstruct.version}` used everywhere. Confirm Testcontainers/GreenMail versions are pinned in the root BOM; if absent, add them there (never leave a dependency unpinned).

- [ ] **Step 2: Rename the package.** Move every file under `excaption/` to `exception/` and change `package ...excaption;` → `package ...exception;` plus all imports repo-wide in this module.

Run: `grep -rn "excaption" notification/src` → Expected: no matches.

- [ ] **Step 3: Verify it compiles.**

Run: `./mvnw -f notification/pom.xml clean compile`
Expected: `BUILD SUCCESS` (email wiring is fixed in Task 2; if `sendSimpleMail` still fails to resolve, that is expected until Task 2 — compile the config/dto changes only, or do Task 2 before re-running).

- [ ] **Step 4: Commit.**

```bash
git add notification/pom.xml notification/src
git commit -m "build(notification): drop legacy deps, add validation/redis/testcontainers, rename exception package"
```

---

### Task 2: Restore email dispatch & wire the `EmailService` bean

**Files:**
- Modify: `notification/src/main/java/com/ganchevdimitarg/notification/service/EmailServiceImpl.java`
- Modify: `notification/src/main/java/com/ganchevdimitarg/notification/service/EmailService.java`
- Create: `notification/src/main/java/com/ganchevdimitarg/notification/dto/NotificationResponse.java`
- Create: `notification/src/main/java/com/ganchevdimitarg/notification/exception/MailDeliveryException.java`
- Test: `notification/src/test/java/com/ganchevdimitarg/notification/service/EmailServiceImplTest.java`

**Interfaces:**
- Produces:
  - `NotificationResponse(String id, String recipient, String status, LocalDateTime sentAt)` (record)
  - `EmailService.sendSimpleMail(NotificationDto) : NotificationResponse`
  - `MailDeliveryException extends BusinessException` (created in Task 4, so create a temporary local copy here only if Task 4 is not yet done; otherwise depend on it)
- Consumes: `NotificationService.createNotification(NotificationDto)` (Task 5 makes it transactional; signature unchanged).

- [ ] **Step 1: Create `NotificationResponse` record.**

```java
package com.ganchevdimitarg.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(String id, String recipient, String status, LocalDateTime sentAt) {
}
```

- [ ] **Step 2: Simplify `EmailService` interface** (drop the attachment method):

```java
package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;

public interface EmailService {
    NotificationResponse sendSimpleMail(NotificationDto notificationDto);
}
```

- [ ] **Step 3: Write the failing test** (GreenMail SMTP double):

```java
package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void should_sendEmailAndPersist_when_requestIsValid() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(greenMail.getSmtp().getPort());

        NotificationService notificationService = mock(NotificationService.class);
        NotificationDto dto = new NotificationDto("user@test.com", "Hi", "Your order shipped");
        when(notificationService.createNotification(dto))
                .thenReturn(new NotificationDto("user@test.com", "Hi", "Your order shipped"));

        EmailServiceImpl service = new EmailServiceImpl(sender, notificationService);
        // @Value sender field defaulted via reflection in test setup or constructor param
        NotificationResponse resp = service.sendSimpleMail(dto);

        assertThat(resp.status()).isEqualTo("SENT");
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }
}
```

- [ ] **Step 4: Run test to verify it fails.**

Run: `./mvnw -f notification/pom.xml test -Dtest=EmailServiceImplTest`
Expected: FAIL (compilation — `EmailServiceImpl` does not implement `EmailService` / constructor mismatch).

- [ ] **Step 5: Rewrite `EmailServiceImpl`** (remove all commented code, implement the interface):

```java
package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;
import com.ganchevdimitarg.notification.exception.MailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.username:no-reply@e-commerce.local}")
    private String sender;

    @Override
    public NotificationResponse sendSimpleMail(NotificationDto notificationDto) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(notificationDto.recipient());
            mailMessage.setSubject(notificationDto.subject());
            mailMessage.setText(notificationDto.msgBody());
            javaMailSender.send(mailMessage);

            NotificationDto saved = notificationService.createNotification(notificationDto);
            log.info("Email successfully sent to {}", notificationDto.recipient());
            return new NotificationResponse(
                    null, saved.recipient(), "SENT", LocalDateTime.now());
        } catch (MailException e) {
            log.warn("Email delivery failed for {}: {}", notificationDto.recipient(), e.getMessage());
            throw new MailDeliveryException(notificationDto.recipient());
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes.**

Run: `./mvnw -f notification/pom.xml test -Dtest=EmailServiceImplTest`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add notification/src/main/java/com/ganchevdimitarg/notification/service \
        notification/src/main/java/com/ganchevdimitarg/notification/dto/NotificationResponse.java \
        notification/src/test/java/com/ganchevdimitarg/notification/service/EmailServiceImplTest.java
git commit -m "feat(notification): restore email dispatch, return NotificationResponse, drop attachment path"
```

---

### Task 3: Fix `MeasureAspect` thread-safety

**Files:**
- Modify: `notification/src/main/java/com/ganchevdimitarg/notification/aop/MeasureAspect.java`
- Test: `notification/src/test/java/com/ganchevdimitarg/notification/aop/MeasureAspectTest.java`

**Interfaces:**
- Produces: a stateless `@Around` timing aspect safe under concurrent virtual-thread calls.

- [ ] **Step 1: Write the failing test** — invoke the aspect concurrently and assert no exception:

```java
package com.ganchevdimitarg.notification.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.util.concurrent.StructuredTaskScope;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeasureAspectTest {

    @Test
    void should_notThrow_when_invokedConcurrently() {
        MeasureAspect aspect = new MeasureAspect();
        assertThatNoException().isThrownBy(() -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                for (int i = 0; i < 100; i++) {
                    scope.fork(() -> {
                        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
                        Signature sig = mock(Signature.class);
                        when(sig.getName()).thenReturn("sendMail");
                        when(pjp.getSignature()).thenReturn(sig);
                        when(pjp.proceed()).thenReturn("ok");
                        return aspect.measure(pjp);
                    });
                }
                scope.join().throwIfFailed();
            }
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./mvnw -f notification/pom.xml test -Dtest=MeasureAspectTest`
Expected: FAIL — current aspect has no `measure` method and the shared `StopWatch` throws `IllegalStateException`.

- [ ] **Step 3: Replace the aspect** with a stateless `@Around`:

```java
package com.ganchevdimitarg.notification.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Slf4j
public class MeasureAspect {

    @Pointcut("execution(* com.ganchevdimitarg.notification.controller.*.*(..))")
    private void trackAllControllers() {}

    @Around("trackAllControllers()")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.info("Method \"{}\" finished in {}ms",
                    pjp.getSignature().getName(), (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (Throwable ex) {
            log.info("Method \"{}\" failed with \"{}\" after {}ms",
                    pjp.getSignature().getName(), ex.toString(), (System.nanoTime() - start) / 1_000_000);
            throw ex;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./mvnw -f notification/pom.xml test -Dtest=MeasureAspectTest`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add notification/src/main/java/com/ganchevdimitarg/notification/aop/MeasureAspect.java \
        notification/src/test/java/com/ganchevdimitarg/notification/aop/MeasureAspectTest.java
git commit -m "fix(notification): make MeasureAspect thread-safe with per-invocation timing"
```

---

### Task 4: Exception hierarchy → problem+json

**Files:**
- Create: `.../exception/BusinessException.java`, `NotFoundException.java`, `ConflictException.java`, `ValidationException.java`, `MailDeliveryException.java`
- Create: `.../exception/ProblemAuthenticationEntryPoint.java`, `ProblemAccessDeniedHandler.java`
- Replace: `.../exception/ControllerExceptionHandler.java`
- Delete: `.../exception/ErrorMessage.java`, `.../exception/InvalidRequestDataException.java`
- Test: `.../exception/ControllerExceptionHandlerTest.java`

**Interfaces:**
- Produces: `BusinessException(HttpStatus, String code, String message)` with `getStatus()`/`getCode()`; `MailDeliveryException(String recipient)` → 502 `MAIL_DELIVERY_FAILED`.

- [ ] **Step 1: Copy `order`'s exception hierarchy** into the `notification.exception` package (`BusinessException`, `NotFoundException`, `ConflictException`, `ValidationException`, `ProblemAccessDeniedHandler`, `ProblemAuthenticationEntryPoint` — identical code, changed package). Then add:

```java
package com.ganchevdimitarg.notification.exception;

import org.springframework.http.HttpStatus;

public class MailDeliveryException extends BusinessException {
    public MailDeliveryException(String recipient) {
        super(HttpStatus.BAD_GATEWAY, "MAIL_DELIVERY_FAILED",
                "Failed to deliver email to " + recipient);
    }
}
```

- [ ] **Step 2: Write the failing test** (MockMvc slice asserting problem+json):

```java
package com.ganchevdimitarg.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    @Test
    void should_returnProblemDetail_when_businessExceptionThrown() {
        ControllerExceptionHandler handler = new ControllerExceptionHandler();
        var response = handler.handleBusinessException(
                new MailDeliveryException("user@test.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getProperties()).containsKey("code");
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("MAIL_DELIVERY_FAILED");
    }
}
```

- [ ] **Step 3: Run test to verify it fails.**

Run: `./mvnw -f notification/pom.xml test -Dtest=ControllerExceptionHandlerTest`
Expected: FAIL — old handler returns `ErrorMessage`, not `ProblemDetail`.

- [ ] **Step 4: Replace `ControllerExceptionHandler`** with `order`'s `ProblemDetail` version (identical to `order/.../ControllerExceptionHandler.java`, package `notification.exception`), then delete `ErrorMessage.java`.

- [ ] **Step 5: Run test to verify it passes.**

Run: `./mvnw -f notification/pom.xml test -Dtest=ControllerExceptionHandlerTest`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add notification/src/main/java/com/ganchevdimitarg/notification/exception \
        notification/src/test/java/com/ganchevdimitarg/notification/exception
git rm notification/src/main/java/com/ganchevdimitarg/notification/exception/ErrorMessage.java
git commit -m "feat(notification): RFC 9457 problem+json errors, BusinessException hierarchy"
```

---

### Task 5: Bean Validation on the DTO — delete custom validation AOP

**Files:**
- Modify: `.../dto/NotificationDto.java`
- Modify: `.../controller/NotificationController.java`
- Delete: `.../annotation/ValidationRequest.java`, `.../aop/ValidationRequestDtoAspect.java`, `.../validation/ValidateRequest.java`, `.../validation/ValidateRequestImpl.java`, `.../exception/InvalidRequestDataException.java`
- Test: `.../dto/NotificationDtoTest.java`

**Interfaces:**
- Produces: `NotificationDto` with Bean Validation constraints; controller uses `@Valid`.

- [ ] **Step 1: Write the failing test:**

```java
package com.ganchevdimitarg.notification.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDtoTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void should_rejectBlankRecipient_when_recipientMissing() {
        var dto = new NotificationDto("", "Subject", "A valid body over ten chars");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void should_pass_when_allFieldsValid() {
        var dto = new NotificationDto("user@test.com", "Subject", "A valid body over ten chars");
        assertThat(validator.validate(dto)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./mvnw -f notification/pom.xml test -Dtest=NotificationDtoTest`
Expected: FAIL — no constraints yet.

- [ ] **Step 3: Add constraints to `NotificationDto`:**

```java
package com.ganchevdimitarg.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationDto(
        @NotBlank @Email String recipient,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(min = 10, max = 251) String msgBody) {
}
```

- [ ] **Step 4: Update the controller** — `@Valid`, remove `@ValidationRequest`, return `NotificationResponse`, single consistent scope, drop the attachment endpoint:

```java
package com.ganchevdimitarg.notification.controller;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;
import com.ganchevdimitarg.notification.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final EmailService emailService;

    @Operation(summary = "Send email", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/send-email")
    @PreAuthorize("hasAuthority('SCOPE_notification.write')")
    public ResponseEntity<NotificationResponse> sendMail(
            @RequestBody @Valid NotificationDto notificationDto) {
        return ResponseEntity.ok(emailService.sendSimpleMail(notificationDto));
    }
}
```

- [ ] **Step 5: Delete the custom validation stack.**

```bash
git rm notification/src/main/java/com/ganchevdimitarg/notification/annotation/ValidationRequest.java \
       notification/src/main/java/com/ganchevdimitarg/notification/aop/ValidationRequestDtoAspect.java \
       notification/src/main/java/com/ganchevdimitarg/notification/validation/ValidateRequest.java \
       notification/src/main/java/com/ganchevdimitarg/notification/validation/ValidateRequestImpl.java
```

- [ ] **Step 6: Run tests to verify pass + module compiles.**

Run: `./mvnw -f notification/pom.xml test -Dtest=NotificationDtoTest`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add notification/src
git commit -m "refactor(notification): Bean Validation on DTO, drop custom validation AOP, align scope"
```

---

### Task 6: Align security config with `order`

**Files:**
- Modify: `.../config/ResourceServerConfig.java`

**Interfaces:**
- Consumes: `ProblemAuthenticationEntryPoint`, `ProblemAccessDeniedHandler` (Task 4).
- Produces: `@EnableMethodSecurity`, health-only public actuator, problem+json auth errors.

- [ ] **Step 1: Update `ResourceServerConfig`** to mirror `order`'s: add `@EnableMethodSecurity`, inject the two problem handlers, wire `.exceptionHandling(...)`, and change the actuator matcher:

```java
.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
.requestMatchers("/api/v1/notification/**").hasAuthority("SCOPE_notification.write")
.anyRequest().authenticated())
```

(Keep the dual JWT/opaque `AuthenticationManagerResolver` exactly as `order` has it.)

- [ ] **Step 2: Verify the context starts** (covered fully in Task 9 IT; here just compile).

Run: `./mvnw -f notification/pom.xml compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit.**

```bash
git add notification/src/main/java/com/ganchevdimitarg/notification/config/ResourceServerConfig.java
git commit -m "fix(notification): consistent scope, method security, problem+json auth errors, health-only actuator"
```

---

### Task 7: Data model — audit columns, entity, soft-delete, `@Transactional`

**Files:**
- Create: `notification/src/main/resources/db/migration/V2__notification_audit_columns.sql`
- Modify: `.../domain/Notification.java`
- Modify: `.../dao/NotificationDao.java`
- Modify: `.../service/NotificationServiceImpl.java`
- Test: `.../NotificationPersistenceIT.java` (extends `AbstractIntegrationTest` from Task 9)

**Interfaces:**
- Produces: `Notification` entity with `createdAt/updatedAt/deletedAt`; `NotificationDao.findActiveById(String)`.

- [ ] **Step 1: Write `V2` migration** (never edit `V1`):

```sql
-- V2__notification_audit_columns.sql
ALTER TABLE notification RENAME COLUMN created_on TO created_at;
ALTER TABLE notification ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notification ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE notification ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notification ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_notification_updated_at ON notification;
CREATE TRIGGER trg_notification_updated_at
    BEFORE UPDATE ON notification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
```

- [ ] **Step 2: Update the `Notification` entity** — audit fields, drop `@AllArgsConstructor`:

```java
package com.ganchevdimitarg.notification.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter @Setter @NoArgsConstructor @Builder
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE) // required by @Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;

    @Column(name = "recipient", columnDefinition = "TEXT", nullable = false)
    private String recipient;

    @Column(name = "subject", columnDefinition = "TEXT", nullable = false)
    private String subject;

    @Column(name = "msg_body", columnDefinition = "TEXT", nullable = false)
    private String msgBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

Note: the `attachment` column stays in the table (nullable) but is dropped from the entity since the attachment feature is removed; the entity simply does not map it.

- [ ] **Step 3: Update the mapper** — `MapStructMapper` no longer references `attachment`; verify it maps `recipient/subject/msgBody` only (it already does, since `NotificationDto` has no attachment). No change expected; run `compile`.

- [ ] **Step 4: Add soft-delete query to `NotificationDao`:**

```java
package com.ganchevdimitarg.notification.dao;

import com.ganchevdimitarg.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationDao extends JpaRepository<Notification, String> {

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.deletedAt IS NULL")
    Optional<Notification> findActiveById(@Param("id") String id);
}
```

- [ ] **Step 5: Make `createNotification` transactional** and set audit timestamps:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationDao notificationDao;
    private final MapStructMapper mapper;

    @Override
    @Transactional
    public NotificationDto createNotification(NotificationDto notificationDto) {
        Notification notification = mapper.mapNotificationDtoToNotification(notificationDto);
        LocalDateTime now = LocalDateTime.now();
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        Notification saved = notificationDao.saveAndFlush(notification);
        log.info("Notification {} persisted", saved.getId());
        return mapper.mapNotificationToNotificationDto(saved);
    }
}
```

- [ ] **Step 6: Run Flyway validate + compile.**

Run: `./mvnw -f notification/pom.xml compile flyway:validate`
Expected: `BUILD SUCCESS` (validate runs against a live DB in CI; locally it may be skipped — the IT in Task 9 exercises the migration via Testcontainers).

- [ ] **Step 7: Commit.**

```bash
git add notification/src/main/resources/db/migration/V2__notification_audit_columns.sql \
        notification/src/main/java/com/ganchevdimitarg/notification/domain \
        notification/src/main/java/com/ganchevdimitarg/notification/dao \
        notification/src/main/java/com/ganchevdimitarg/notification/service/NotificationServiceImpl.java
git commit -m "feat(notification): audit columns, soft-delete query, transactional persistence"
```

---

### Task 8: Kafka hardening — typed consumer, DLT, idempotency

**Files:**
- Create: `.../config/KafkaTopics.java`, `.../config/JacksonConfig.java`, `.../config/RedisConfig.java`, `.../service/IdempotencyService.java`
- Modify: `.../config/KafkaConsumerConfig.java`, `.../listener/KafkaListenerService.java`
- Test: `.../listener/KafkaListenerServiceIT.java` (Task 9 base)

**Interfaces:**
- Produces:
  - `KafkaTopics.NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested"`, `...DLT`
  - `IdempotencyService.runOnce(String key, Supplier<NotificationResponse>) : NotificationResponse`
  - Listener consuming typed `NotificationDto`.

- [ ] **Step 1: Add `KafkaTopics` constants** (mirror `order/.../KafkaTopics.java`):

```java
package com.ganchevdimitarg.notification.config;

public final class KafkaTopics {
    public static final String NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested";
    public static final String NOTIFICATION_EMAIL_REQUESTED_DLT = "notification.email.requested.DLT";
    public static final String GROUP = "notification-group";
    private KafkaTopics() {}
}
```

- [ ] **Step 2: Add a shared `ObjectMapper` bean** (`JacksonConfig`) with JavaTimeModule + `NON_NULL` + `FAIL_ON_UNKNOWN_PROPERTIES=false`, and a `RedisConfig` with `GenericJackson2JsonRedisSerializer` (copy `order`'s `RedisConfig`). No `new ObjectMapper()` anywhere.

- [ ] **Step 3: Rewrite `KafkaConsumerConfig`** — typed `JsonDeserializer<NotificationDto>`, group id from `KafkaTopics.GROUP`, and a `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` publishing to `...DLT` after retries (mirror root `docs/context/kafka.md`). Deserializer trusts the `notification.dto` package.

- [ ] **Step 4: Add `IdempotencyService`** backed by Redis `RedisTemplate<String,String>` using `setIfAbsent` with 24h TTL on key `idempotency:notification:<key>`; skip the send on a hit, log and return a `SKIPPED` response.

- [ ] **Step 5: Write the failing listener IT** (Testcontainers Kafka + GreenMail): publish a `NotificationDto` JSON to `notification.email.requested`, assert (Awaitility) exactly one GreenMail message and one persisted row; publish the same idempotency key twice → still one email.

- [ ] **Step 6: Rewrite `KafkaListenerService`:**

```java
package com.ganchevdimitarg.notification.listener;

import com.ganchevdimitarg.notification.config.KafkaTopics;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import com.ganchevdimitarg.notification.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerService {

    private final EmailService emailService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_EMAIL_REQUESTED,
                   groupId = KafkaTopics.GROUP, containerFactory = "messageListener")
    public void onEmailRequested(@Payload NotificationDto dto,
                                 @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String idemKey = key != null ? key : dto.recipient() + ":" + dto.subject();
        idempotencyService.runOnce(idemKey, () -> emailService.sendSimpleMail(dto));
    }
}
```

- [ ] **Step 7: Run the listener IT.**

Run: `./mvnw -f notification/pom.xml test -Dtest=KafkaListenerServiceIT`
Expected: PASS (both the single-send and dedupe assertions).

- [ ] **Step 8: Commit.**

```bash
git add notification/src
git commit -m "feat(notification): typed Kafka consumer, DLT error handler, Redis idempotency guard"
```

---

### Task 9: Test infrastructure — `AbstractIntegrationTest` + controller IT

**Files:**
- Create: `notification/src/test/java/com/ganchevdimitarg/notification/AbstractIntegrationTest.java`
- Create: `notification/src/test/java/com/ganchevdimitarg/notification/controller/NotificationControllerIT.java`
- Modify: `notification/src/test/resources/application-test.properties`

**Interfaces:**
- Produces: shared Postgres `@ServiceConnection` + Redis + Kafka + GreenMail base for all ITs.

- [ ] **Step 1: Create `AbstractIntegrationTest`** — copy `order`'s (Postgres `@ServiceConnection`, `@ActiveProfiles("test")`), add Kafka + Redis containers with `@DynamicPropertySource` (`kafka.bootstrapAddress`, `spring.kafka.bootstrap-servers`, `spring.data.redis.host/port`), and configure GreenMail SMTP host/port into `spring.mail.*`.

- [ ] **Step 2: Add test props** — point `spring.mail.host/port` at a dynamic GreenMail port; keep `ddl-auto=validate`, Flyway enabled, Eureka off (already present).

- [ ] **Step 3: Write `NotificationControllerIT`** — `@SpringBootTest` + MockMvc + `spring-security-test`: `should_return200AndSendEmail_when_authorized` (mock JWT authority `SCOPE_notification.write`, assert GreenMail received + 200 `NotificationResponse`), `should_return403_when_scopeMissing`, `should_return400ProblemJson_when_bodyInvalid`.

- [ ] **Step 4: Run the full suite.**

Run: `./mvnw -f notification/pom.xml clean verify`
Expected: `BUILD SUCCESS`, coverage ≥ 80% line / 100% domain.

- [ ] **Step 5: Commit.**

```bash
git add notification/src/test
git commit -m "test(notification): Testcontainers base + controller/listener integration tests"
```

---

### Task 10: Docker & run config

**Files:**
- Create: `notification/Dockerfile`
- Create: `.run/notification.xml`

- [ ] **Step 1: Create the Dockerfile** — copy `order/Dockerfile`, changing `order`→`notification`, port `8086`→`8085`, jar `notification.jar`. Confirm `spring-boot-maven-plugin` produces `notification.jar` (add `<finalName>notification</finalName>` to the build if needed).

- [ ] **Step 2: Create `.run/notification.xml`** IntelliJ Spring Boot run config (main class `com.ganchevdimitarg.notification.NotificationApplication`, active profile `dev`).

- [ ] **Step 3: Build the image to verify.**

Run: `docker build -f notification/Dockerfile -t notification:dev .` (from repo root)
Expected: image builds; `notification.jar` copied.

- [ ] **Step 4: Commit.**

```bash
git add notification/Dockerfile .run/notification.xml
git commit -m "build(notification): multi-stage Dockerfile + IntelliJ run config"
```

---

### Task 11: Final verification & module CLAUDE.md update

**Files:**
- Modify: `notification/CLAUDE.md` (remove the "Known migration gaps / does NOT compile" note)

- [ ] **Step 1: Full green build + lint + Flyway.**

```bash
./mvnw -f notification/pom.xml clean verify
./mvnw -f notification/pom.xml checkstyle:check
```
Expected: all green.

- [ ] **Step 2: Update `notification/CLAUDE.md`** — delete the "Known migration gaps" section; note the module is now Boot-4 compliant.

- [ ] **Step 3: Update the memory index** — mark `notification` as migrated in `boot4-migration-status`.

- [ ] **Step 4: Commit.**

```bash
git add notification/CLAUDE.md
git commit -m "docs(notification): mark module Boot-4 migrated"
```

---

## Self-Review Notes

- **Spec coverage:** Phases 0–8 of the spec map to Tasks 1–11 (Phase 0→T1, Phase 1→T2, Phase 2→T3/T6, Phase 3→T4, Phase 4→T5, Phase 5→T7, Phase 6→T8, Phase 7→T9, Phase 8→T10, plus T11 closeout). Resolved decisions (Redis idempotency, remove attachment, `NotificationResponse`) are in T8/T5/T2 respectively.
- **Ordering caveat:** `MailDeliveryException` is referenced in Task 2 but created in Task 4. Execute Task 4 before Task 2, **or** create `MailDeliveryException` as the first step of Task 2. Recommended execution order: **1 → 4 → 2 → 3 → 5 → 6 → 7 → 8 → 9 → 10 → 11.**
- **Type consistency:** `EmailService.sendSimpleMail` returns `NotificationResponse` in T2, consumed unchanged in T5 (controller) and T8 (listener via `IdempotencyService.runOnce`). `NotificationDao.findActiveById` and audit field names are consistent across T7/T8.
- **`@AllArgsConstructor` note:** T7's entity uses a private `@AllArgsConstructor` purely to satisfy `@Builder`; this is allowed by `CLAUDE.md` (entities may use `@Builder`). If checkstyle objects, drop `@Builder` and construct via setters.
