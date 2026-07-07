# Payment Service — Grade-A Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the `payment` module to Grade A (≥ 9/10 on every audit dimension) by committing its already-green Boot-4 refactor as clean history, restoring the accidentally-removed refund endpoint, closing the double-charge / orphaned-charge risk, and adding the missing convention and testability infrastructure.

**Architecture:** The refactor's layering (thin controllers → `@Transactional`/`@PreAuthorize` services → a `PaymentGateway` port over Stripe → `Optional`-returning DAOs → `Auditable` JPA entities → problem+json `@RestControllerAdvice`) is preserved, not redesigned. The provider (Stripe) call is moved **outside** the DB transaction and given a Stripe idempotency key so a retry cannot double-charge; the local row is written afterwards by a separate `@Transactional` collaborator bean (avoiding Spring's self-invocation proxy trap).

**Tech Stack:** Java 25, Spring Boot 4.1.0 (WebMVC), Spring Security (OAuth2 resource server, JWT + opaque), Spring Data JPA + PostgreSQL + Flyway, Spring Data Redis (Lettuce), Spring Cloud CircuitBreaker/Resilience4j, Stripe Java SDK, JUnit 5 + AssertJ + Mockito + Testcontainers.

## Global Constraints

*(Every task's requirements implicitly include this section. Values copied verbatim from the spec.)*

- Package root `com.ganchevdimitarg.payment`; port 8087; PostgreSQL, own schema, Flyway-owned. Existing max migration = **V2** → next = **V3**.
- British English in prose/comments; Conventional Commits subjects — **confirm every commit subject with the maintainer before committing**.
- Records for all immutable DTOs/commands/responses. JPA entities use explicit Lombok (`@Getter @Setter @NoArgsConstructor` (+`@Builder`)) — **never** `@Data`, **never** `@AllArgsConstructor`, **never** `import lombok.*`.
- `@Transactional` on the service layer only; `@PreAuthorize` on the service layer only; repositories return `Optional<T>`, unwrapped via `orElseThrow`. Never `Optional.get()` without a guard; never `assert` as a runtime guard.
- All errors flow through `BusinessException` → `ControllerExceptionHandler` → `application/problem+json` (RFC 9457).
- **Balances/amounts are strongly consistent — never cached.** The only Redis payload is the idempotency dedupe marker.
- Redis: `GenericJackson2JsonRedisSerializer` (never Java serialization); idempotency key `payment:idempotency:<key>`; TTL always 24h.
- Flyway: `V<n>__snake_case.sql`, two underscores, monotonically increasing, never edit a committed migration; `ddl-auto=validate`; never `spring.flyway.enabled=false`.
- Integration tests extend `AbstractIntegrationTest` (real Postgres + Redis via Testcontainers); never H2/embedded; no `Thread.sleep` (use Awaitility); test names `should_<behaviour>_when_<condition>`.
- Reactive types (`Mono`/`Flux`/WebFlux) are forbidden outside `gateway` — payment is WebMVC.
- OAuth2 scopes `payment.read`/`payment.write` → authorities `SCOPE_payment.read`/`SCOPE_payment.write`.

## Environment Caveats (hard-won — apply to every task)

- **Root reactor is BLOCKED.** Build payment STANDALONE: `./mvnw -f payment/pom.xml ...`.
- Payment depends on the `client` artifact → install it first: `./mvnw -f client/pom.xml install -DskipTests`.
- Do **not** gate on `checkstyle:check` if it reports pre-existing module-wide violations — verify via `clean verify`; run checkstyle for signal only.
- **Until Task 8 adds the maven-failsafe binding, `*IT` tests do NOT run in `verify`.** Run an IT explicitly with `./mvnw -f payment/pom.xml test -Dtest=<ITName>` (surefire will pick it up when named directly), or rely on the failsafe binding once Task 8 lands.
- The working tree has been observed mutating from an external editor. **Before Task 0 commits anything, re-run `clean verify` to confirm green on the current tree.** If a build fails on a file you did not change, re-snapshot before proceeding.
- Testcontainers: Postgres uses the singleton `@ServiceConnection` pattern (started in a static block). Redis (added in Task 7) is a `GenericContainer("redis:7")` wired via `@DynamicPropertySource`.

---

## Task 0: Baseline — commit the green refactor (CONTROLLER-EXECUTED groundwork)

> **Not a dispatched TDD task.** The controlling session performs this directly because it is groundwork over a pre-existing uncommitted working tree. Its output — a committed baseline `HEAD` — is the SDD **base commit** for every later task's review package.

**Files:** all modified/created/deleted `payment/` paths currently in the working tree (see `git status payment/`). **Payment paths only** — never stage `authentication/`, `order/`, `notification/`, or root `README.md`.

- [ ] **Step 1: Confirm the tree is green**

Run: `./mvnw -f client/pom.xml install -DskipTests && ./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS, `Tests run: 18, Failures: 0, Errors: 0`.

- [ ] **Step 2: Remove junk from the payment tree**

Delete crash dumps: every `*.stackdump` under `payment/` (e.g. `payment/sh.exe.stackdump`, `payment/src/main/java/com/ganchevdimitarg/payment/sh.exe.stackdump`, `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/sh.exe.stackdump`).

- [ ] **Step 3: Verify the index is clean before staging** (a prior session left renames pre-staged)

Run: `git reset` (unstages everything without touching the working tree), then `git diff --cached --name-only` → expect empty.

- [ ] **Step 4: Stage ONLY payment source paths, excluding untracked noise**

Stage explicitly (never `git add -A`). Include: `payment/pom.xml`, all `payment/src/main/**` and `payment/src/test/**` modified/created/deleted files (including the `ChargeServerImpl.java` → `ChargeServiceImpl.java` rename and the `excaption/` → `exception/` package changes).
**Exclude** (do not stage): `payment/.mcp.json`, `payment/MEMORY.md`, `payment/SETUP.md`, `payment/decisions.md` (committed in Task 12), `payment/docs/`.

- [ ] **Step 5: Confirm subjects with the maintainer, then commit as a small number of coherent commits**

Proposed slicing (confirm subjects first). If a clean split from the interdependent snapshot proves infeasible, fall back to ONE atomic commit — confirmed with the maintainer:
1. `build(payment)!: migrate to Spring Boot 4 / Java 25`
2. `refactor(payment)!: Boot-4 core — security, gateway port, exception model, DTOs`
3. `feat(payment): audit columns + optimistic locking (V2)`
4. `feat(payment): structured JSON logging`
5. `test(payment): Testcontainers unit + IT baseline`

- [ ] **Step 6: Re-verify green at the baseline HEAD**

Run: `./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS, 18 tests. Record this `HEAD` sha as the SDD base commit.

---

## Task 1: Restore refund as a first-class, tested endpoint

**Files:**
- Create: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/GatewayRefund.java`
- Create: `payment/src/main/java/com/ganchevdimitarg/payment/dto/RefundChargeCommand.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/dao/ChargeDao.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/ChargeService.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImpl.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/controller/ChargeController.java`
- Test: `payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImplTest.java`
- Test: `payment/src/test/java/com/ganchevdimitarg/payment/controller/ChargeControllerTest.java`

**Interfaces:**
- Produces: `record GatewayRefund(String id, String charge, String status)`; `record RefundChargeCommand(@NotBlank String chargeId)`; `GatewayRefund PaymentGateway.refundCharge(String chargeId)`; `ChargeResponse ChargeService.refund(RefundChargeCommand command)`; `Optional<AppCharge> ChargeDao.findByChargeId(String chargeId)`.
- Consumes: existing `ChargeResponse(String chargeId, String status)`, `NotFoundException(String resource, Object id)`, the `call(...)` helper in `StripePaymentGateway`.

- [ ] **Step 1: Write the failing service tests** (add to `ChargeServiceImplTest`, keeping the existing two tests):

```java
    @Test
    void should_refundKnownCharge_when_chargeExists() {
        AppCharge charge = AppCharge.builder().chargeId("ch_1").amount(500L).build();
        when(chargeDao.findByChargeId("ch_1")).thenReturn(Optional.of(charge));
        when(paymentGateway.refundCharge("ch_1"))
                .thenReturn(new GatewayRefund("re_1", "ch_1", "succeeded"));

        ChargeResponse response = chargeService.refund(new RefundChargeCommand("ch_1"));

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
    }

    @Test
    void should_throwNotFound_when_refundingUnknownCharge() {
        when(chargeDao.findByChargeId("ch_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.refund(new RefundChargeCommand("ch_missing")))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).refundCharge(any());
    }
```
Add imports to the test: `com.ganchevdimitarg.payment.dto.RefundChargeCommand`, `com.ganchevdimitarg.payment.gateway.GatewayRefund`, and `static org.mockito.Mockito.never`.

- [ ] **Step 2: Run the tests — expect failure**

Run: `./mvnw -f payment/pom.xml test -Dtest=ChargeServiceImplTest`
Expected: FAIL — compile errors (`RefundChargeCommand`, `GatewayRefund`, `refund`, `findByChargeId` returns `AppCharge` not `Optional`).

- [ ] **Step 3: Create `GatewayRefund`:**

```java
package com.ganchevdimitarg.payment.gateway;

public record GatewayRefund(String id, String charge, String status) {
}
```

- [ ] **Step 4: Create `RefundChargeCommand`:**

```java
package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for refunding a charge in full. */
public record RefundChargeCommand(@NotBlank String chargeId) {
}
```

- [ ] **Step 5: Add `refundCharge` to the port** (`PaymentGateway.java`, after `createCharge`):

```java
    GatewayRefund refundCharge(String chargeId);
```

- [ ] **Step 6: Implement `refundCharge` in `StripePaymentGateway`** (add after `createCharge`; add `import com.stripe.model.Refund;`):

```java
    @Override
    public GatewayRefund refundCharge(String chargeId) {
        return call(() -> {
            // No amount => Stripe refunds the charge in full, which is what a
            // compensating refund requires.
            Refund refund = Refund.create(Map.of("charge", chargeId));
            log.info("Stripe refundCharge successful: {}", refund.getId());
            return new GatewayRefund(refund.getId(), refund.getCharge(), refund.getStatus());
        });
    }
```

- [ ] **Step 7: Change `ChargeDao.findByChargeId` to return `Optional`:**

```java
package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.domain.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargeDao extends JpaRepository<AppCharge, String> {
    Optional<AppCharge> findByChargeId(String chargeId);
}
```

- [ ] **Step 8: Add `refund` to the `ChargeService` interface:**

```java
package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;

public interface ChargeService {
    ChargeResponse createCharge(CreateChargeCommand command);

    ChargeResponse refund(RefundChargeCommand command);
}
```

- [ ] **Step 9: Implement `refund` in `ChargeServiceImpl`** (add the method; add imports `com.ganchevdimitarg.payment.dto.RefundChargeCommand`, `com.ganchevdimitarg.payment.gateway.GatewayRefund`):

```java
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse refund(RefundChargeCommand command) {
        AppCharge charge = chargeDao.findByChargeId(command.chargeId())
                .orElseThrow(() -> {
                    log.warn("Charge {} does not exist in db charges", command.chargeId());
                    return new NotFoundException("Charge", command.chargeId());
                });

        GatewayRefund refund = paymentGateway.refundCharge(charge.getChargeId());

        log.info("Method refund: refunded charge {} with status {}", command.chargeId(), refund.status());
        return new ChargeResponse(command.chargeId(), refund.status());
    }
```

- [ ] **Step 10: Add the controller endpoint** (`ChargeController.java`, add imports `com.ganchevdimitarg.payment.dto.RefundChargeCommand`):

```java
    @PostMapping("/refund-charge")
    public ChargeResponse refundCharge(@RequestBody @Valid RefundChargeCommand command) {
        return chargeService.refund(command);
    }
```

- [ ] **Step 11: Add the failing controller test** (append to `ChargeControllerTest`):

```java
    @Test
    void should_return200AndCharge_when_refundValid() throws Exception {
        when(chargeService.refund(any())).thenReturn(new ChargeResponse("ch_1", "succeeded"));

        mockMvc.perform(post("/api/v1/payment/charge/refund-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundChargeCommand("ch_1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeId").value("ch_1"));
    }

    @Test
    void should_return400_when_refundChargeIdBlank() throws Exception {
        mockMvc.perform(post("/api/v1/payment/charge/refund-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundChargeCommand(""))))
                .andExpect(status().isBadRequest());
    }
```
Add import `com.ganchevdimitarg.payment.dto.RefundChargeCommand`. Note: `standaloneSetup` does not run Bean Validation on `@RequestBody` unless a validator is present; the `@Valid` + default `LocalValidatorFactoryBean` in `MockMvcBuilders.standaloneSetup(...)` applies. If the blank-id test does not produce 400 under standalone setup, wire the validator explicitly: `.setValidator(new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean())` on the builder in `setUp()`.

- [ ] **Step 12: Run the tests — expect pass**

Run: `./mvnw -f payment/pom.xml test -Dtest=ChargeServiceImplTest,ChargeControllerTest`
Expected: PASS.

- [ ] **Step 13: Full verify + commit**

Run: `./mvnw -f payment/pom.xml clean verify` → BUILD SUCCESS.
```bash
git add payment/src/main/java/com/ganchevdimitarg/payment/gateway/GatewayRefund.java \
        payment/src/main/java/com/ganchevdimitarg/payment/dto/RefundChargeCommand.java \
        payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/dao/ChargeDao.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/ChargeService.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImpl.java \
        payment/src/main/java/com/ganchevdimitarg/payment/controller/ChargeController.java \
        payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImplTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/controller/ChargeControllerTest.java
git commit -m "feat(payment): restore full-refund endpoint"
```

---

## Task 2: Charge safety — Stripe idempotency key + charge persisted outside the transaction

**Files:**
- Create: `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargePersistence.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/ChargeService.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImpl.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/controller/ChargeController.java`
- Test: `payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImplTest.java`

**Interfaces:**
- Produces: `GatewayCharge PaymentGateway.createCharge(ChargeRequest request, String idempotencyKey)`; `ChargeResponse ChargeService.createCharge(CreateChargeCommand command, String idempotencyKey)`; `@Component ChargePersistence` with `@Transactional void persistCharge(GatewayCharge charge, AppCustomer customer)`.
- Consumes: existing `GatewayCharge`, `ChargeRequest`, `AppCharge` builder, `ChargeDao`.

**Why a separate bean:** a `@Transactional` method invoked from another method of the *same* bean bypasses the Spring proxy and runs with **no** transaction. The persistence step therefore lives on a separate injected `ChargePersistence` bean; the public `createCharge` on `ChargeServiceImpl` is **not** `@Transactional`, so the Stripe call is not enrolled in a DB transaction.

- [ ] **Step 1: Update the two existing `createCharge` service tests to the new signature and add key pass-through assertions** (in `ChargeServiceImplTest`). Replace the body of `should_chargeGatewayAndPersist_when_customerExists` and `should_throwNotFound_when_customerMissing`, and add a mock for the new collaborator:

```java
    @Mock
    private ChargePersistence chargePersistence;
```
(add to the fields; `@InjectMocks` will supply it). New/updated tests:

```java
    @Test
    void should_chargeWithIdempotencyKeyAndPersist_when_customerExists() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));
        GatewayCharge gatewayCharge = new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded");
        when(paymentGateway.createCharge(any(ChargeRequest.class), eq("idem-123"))).thenReturn(gatewayCharge);

        ChargeResponse response = chargeService.createCharge(command(), "idem-123");

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        verify(paymentGateway).createCharge(any(ChargeRequest.class), eq("idem-123"));
        verify(chargePersistence).persistCharge(gatewayCharge, customer);
    }

    @Test
    void should_notCallGateway_when_customerMissing() {
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.createCharge(command(), "idem-123"))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).createCharge(any(), any());
        verify(chargePersistence, never()).persistCharge(any(), any());
    }
```
Add imports: `static org.mockito.ArgumentMatchers.eq`, `static org.mockito.Mockito.never`. Remove the old `chargeDao`/`AppCharge` capture assertions from the replaced test (persistence now lives in `ChargePersistence`, covered by its own path).

- [ ] **Step 2: Run — expect failure**

Run: `./mvnw -f payment/pom.xml test -Dtest=ChargeServiceImplTest`
Expected: FAIL (compile — new `createCharge` arity, `ChargePersistence` missing).

- [ ] **Step 3: Change the port signature** (`PaymentGateway.java`) — replace `createCharge`:

```java
    GatewayCharge createCharge(ChargeRequest request, String idempotencyKey);
```

- [ ] **Step 4: Pass the key to Stripe** (`StripePaymentGateway.java`) — replace `createCharge`; add `import com.stripe.net.RequestOptions;`:

```java
    @Override
    public GatewayCharge createCharge(ChargeRequest request, String idempotencyKey) {
        return call(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", request.amount());
            params.put("currency", request.currency());
            params.put("receipt_email", request.receiptEmail());
            params.put("customer", request.customerId());
            params.put("source", request.source());
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            Charge charge = Charge.create(params, options);
            log.info("Stripe createCharge successful: {}", charge.getId());
            return new GatewayCharge(charge.getId(), charge.getAmount(), charge.getCurrency(),
                    charge.getCustomer(), charge.getReceiptEmail(), charge.getStatus());
        });
    }
```

- [ ] **Step 5: Create the `ChargePersistence` collaborator:**

```java
package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a confirmed charge in its own transaction. Isolated on a separate bean so the
 * transactional boundary is honoured via the Spring proxy — the calling service invokes the
 * provider outside any transaction, then delegates here to write the local row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargePersistence {

    private final ChargeDao chargeDao;

    @Transactional
    public void persistCharge(GatewayCharge charge, AppCustomer customer) {
        chargeDao.saveAndFlush(AppCharge.builder()
                .chargeId(charge.id())
                .amount(charge.amount())
                .currency(charge.currency())
                .customerId(charge.customerId())
                .receiptEmail(charge.receiptEmail())
                .customer(customer)
                .build());
        log.info("Persisted charge: {}", charge.id());
    }
}
```

- [ ] **Step 6: Rewrite `ChargeServiceImpl.createCharge`** — provider call outside a transaction, persistence delegated. Replace the method and the fields/constructor injection; remove `@Transactional` from `createCharge`, remove the direct `chargeDao`/`AppCharge`/`ChargeRequest`/`GatewayCharge` save usage (keep imports still needed). New field set + method:

```java
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;
    private final ChargePersistence chargePersistence;
    // NOTE: chargeDao stays a field only if still used by refund(); refund() uses chargeDao, so keep it.
```
```java
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse createCharge(CreateChargeCommand command, String idempotencyKey) {
        AppCustomer appCustomer = customerDao.findByUsername(command.username()).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", command.username());
            return new NotFoundException("Customer", command.username());
        });

        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot double-charge. The local row is written afterwards.
        GatewayCharge charge = paymentGateway.createCharge(
                new ChargeRequest(command.amount(), command.currency(), command.receiptEmail(),
                        command.customerId(), command.cardId()),
                idempotencyKey);

        chargePersistence.persistCharge(charge, appCustomer);

        log.info("Method createCharge: Create successful charge: {}", charge.id());
        return new ChargeResponse(charge.id(), charge.status());
    }
```
Keep `refund(...)` from Task 1 unchanged (it stays `@Transactional` and uses `chargeDao`). Ensure `chargeDao` remains an injected field (used by `refund`).

- [ ] **Step 7: Update the `ChargeService` interface** — change `createCharge`:

```java
    ChargeResponse createCharge(CreateChargeCommand command, String idempotencyKey);
```

- [ ] **Step 8: Pass the header at the controller** (`ChargeController.createCharge`) — add `import org.springframework.web.bind.annotation.RequestHeader;`:

```java
    @PostMapping("/create-charge")
    public ChargeResponse createCharge(
            @RequestBody @Valid CreateChargeCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return chargeService.createCharge(command, idempotencyKey);
    }
```
*(The `IdempotencyInterceptor` already rejects a write with no `Idempotency-Key` (400) before the controller runs, so `required = false` here is safe; the value is guaranteed present.)*

- [ ] **Step 9: Fix the existing controller test** (`ChargeControllerTest.should_return200AndCharge_when_createValid`) — the mock now needs a header and the two-arg service call. Update the stub to `when(chargeService.createCharge(any(), any())).thenReturn(...)` and add `.header("Idempotency-Key", "idem-1")` to the `create-charge` request. The `should_return400_when_amountNotPositive` test also needs the header added (or it will 400 for the missing header instead of validation — either way 400, but add the header to keep the assertion about validation precise).

- [ ] **Step 10: Run the service + controller tests — expect pass**

Run: `./mvnw -f payment/pom.xml test -Dtest=ChargeServiceImplTest,ChargeControllerTest`
Expected: PASS.

- [ ] **Step 11: Full verify + commit**

Run: `./mvnw -f payment/pom.xml clean verify` → BUILD SUCCESS.
```bash
git add payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargePersistence.java \
        payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/ChargeService.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImpl.java \
        payment/src/main/java/com/ganchevdimitarg/payment/controller/ChargeController.java \
        payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeServiceImplTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/controller/ChargeControllerTest.java
git commit -m "fix(payment): charge with Stripe idempotency key outside the DB transaction"
```

---

## Task 3: Apply provider-outside-transaction to createCustomer and createCard

**Files:**
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java`
- Create: `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CustomerPersistence.java`
- Create: `payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CardPersistence.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/CustomerService.java`, `CustomerServiceImpl.java`, `CustomerController.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/service/CardService.java`, `CardServiceImpl.java`, `CardController.java`
- Test: `payment/src/test/java/com/ganchevdimitarg/payment/service/impl/CustomerServiceImplTest.java`, `CardServiceImplTest.java`

**Interfaces:**
- Produces: `GatewayCustomer PaymentGateway.createCustomer(String email, String name, String idempotencyKey)`; `GatewayCard PaymentGateway.createCard(String customerId, CardDetails card, String idempotencyKey)`; `ChargeResponse`-style responses unchanged; `@Component CustomerPersistence` (`@Transactional CustomerResponse persistCustomer(GatewayCustomer)`); `@Component CardPersistence` (`@Transactional void persistCard(GatewayCard, AppCustomer)`); service `create...` methods gain a trailing `String idempotencyKey` param.

> Follow the **exact same pattern as Task 2**: provider call in the non-transactional public service method, persistence on a separate `@Transactional` collaborator bean, `Idempotency-Key` header threaded from the controller. `deleteCustomer` keeps its existing `@Transactional` + soft-delete (`setDeletedAt`) — only the *create* paths change.

- [ ] **Step 1: Write failing tests** — update `CustomerServiceImplTest` and `CardServiceImplTest` to the new signatures and assert (a) the gateway is called with the idempotency key, (b) persistence is delegated to the collaborator, (c) a missing customer never calls the gateway. Mirror Task 2's test shape (`eq("idem-123")`, `verify(...Persistence).persist...`, `verify(paymentGateway, never())...` on the missing-dependency path). Read the current test files first and preserve their existing non-create tests (e.g. `getCards`, `getCustomerByUsername`, `deleteCustomer`).

- [ ] **Step 2: Run — expect failure**

Run: `./mvnw -f payment/pom.xml test -Dtest=CustomerServiceImplTest,CardServiceImplTest`
Expected: FAIL (compile — new arities, collaborators missing).

- [ ] **Step 3: Change the port** (`PaymentGateway.java`) — replace `createCustomer` and `createCard`:

```java
    GatewayCustomer createCustomer(String email, String name, String idempotencyKey);

    GatewayCard createCard(String customerId, CardDetails card, String idempotencyKey);
```

- [ ] **Step 4: Pass the key to Stripe** (`StripePaymentGateway.java`) — in `createCustomer` and `createCard`, build `RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();` and pass `options` to `Customer.create(params, options)` and to the card-creation `Token.create(...)`/`customer.getSources().create(...)` call (apply the key to the card-creation `create` call). Keep the rest of each method identical.

- [ ] **Step 5: Create `CustomerPersistence`:**

```java
package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerPersistence {

    private final CustomerDao customerDao;

    @Transactional
    public CustomerResponse persistCustomer(GatewayCustomer customer) {
        customerDao.save(AppCustomer.builder()
                .customerId(customer.id())
                .username(customer.email())
                .customerName(customer.name())
                .build());
        log.info("Created customer in payment service db");
        return new CustomerResponse(customer.id(), customer.email(), customer.name());
    }
}
```

- [ ] **Step 6: Create `CardPersistence`:**

```java
package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardPersistence {

    private final CardDao cardDao;

    @Transactional
    public void persistCard(GatewayCard card, AppCustomer customer) {
        cardDao.saveAndFlush(AppCard.builder()
                .cardId(card.id())
                .brand(card.brand())
                .customerId(card.customerId())
                .cvcCheck(card.cvcCheck())
                .expMonth(card.expMonth())
                .expYear(card.expYear())
                .lastFourDigits(card.lastFourDigits())
                .customer(customer)
                .build());
        log.info("Method createCard: Create card successful: {}", card.id());
    }
}
```

- [ ] **Step 7: Rewrite the create paths** in `CustomerServiceImpl.createCustomer` and `CardServiceImpl.createCard` — remove `@Transactional` from the public create method, inject the collaborator, call the gateway (with the key) outside a transaction, then delegate to the collaborator. Keep `@PreAuthorize("hasAuthority('SCOPE_payment.write')")` on the public method. Keep `deleteCustomer`, `getCustomerByUsername`, `getCards`, `getCustomerCards` unchanged. Update the `CustomerService`/`CardService` interface `create...` signatures to add the trailing `String idempotencyKey`.

- [ ] **Step 8: Thread the header at the controllers** — `CustomerController` create-customer and `CardController` create-card gain `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey` and pass it to the service. Read each controller first and add only the header parameter + argument.

- [ ] **Step 9: Run — expect pass**

Run: `./mvnw -f payment/pom.xml test -Dtest=CustomerServiceImplTest,CardServiceImplTest,CustomerControllerTest,CardControllerTest`
Expected: PASS.

- [ ] **Step 10: Full verify + commit**

Run: `./mvnw -f payment/pom.xml clean verify` → BUILD SUCCESS.
```bash
git add payment/src/main/java/com/ganchevdimitarg/payment/gateway/PaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/gateway/StripePaymentGateway.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CustomerPersistence.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CardPersistence.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/CustomerService.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CustomerServiceImpl.java \
        payment/src/main/java/com/ganchevdimitarg/payment/controller/CustomerController.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/CardService.java \
        payment/src/main/java/com/ganchevdimitarg/payment/service/impl/CardServiceImpl.java \
        payment/src/main/java/com/ganchevdimitarg/payment/controller/CardController.java \
        payment/src/test/java/com/ganchevdimitarg/payment/service/impl/CustomerServiceImplTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/service/impl/CardServiceImplTest.java
git commit -m "fix(payment): create customer/card via provider before persisting"
```

---

## Task 4: Entity Lombok cleanup

**Files:**
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCustomer.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCharge.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCard.java`

**Interfaces:** no signature changes — builder + getters/setters remain; only `@AllArgsConstructor` and `import lombok.*` are removed.

- [ ] **Step 1: Remove `@AllArgsConstructor` and the wildcard imports** from all three entities. Replace `import lombok.*;` with explicit imports actually used (`import lombok.Builder; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;`) and replace `import jakarta.persistence.*;` with the explicit `jakarta.persistence` imports each class uses (`Column`, `Entity`, `Table`, `Id`, `GeneratedValue`, `GenerationType`, `Index`, `UniqueConstraint`, `ManyToOne`, `OneToMany`, `JoinColumn`, `FetchType`, `CascadeType` as applicable per class). Keep `@NoArgsConstructor @Builder @Getter @Setter` and the `extends Auditable`.

- [ ] **Step 2: Confirm no positional-constructor callers exist**

Run: `grep -rn "new AppCustomer(\|new AppCharge(\|new AppCard(" payment/src`
Expected: no matches (all construction is via `.builder()`).

- [ ] **Step 3: Full verify — expect pass** (compilation proves no `@AllArgsConstructor` dependency)

Run: `./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 4: Commit**

```bash
git add payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCustomer.java \
        payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCharge.java \
        payment/src/main/java/com/ganchevdimitarg/payment/domain/AppCard.java
git commit -m "refactor(payment): entities builder-only with explicit imports"
```

---

## Task 5: Remove H2; replace the reactive resilience4j starter; tidy config

**Files:**
- Modify: `payment/pom.xml`
- Modify (or delete): `payment/src/test/resources/application-test.properties`
- Create (if flags relocate): `payment/src/test/resources/application-test.yml`
- Modify: `payment/src/main/resources/application.yml`

**Interfaces:** none (build + config only).

- [ ] **Step 1: Remove H2** from `payment/pom.xml` — delete the `com.h2database:h2` `<dependency>` block and the `<h2.version>` property.

- [ ] **Step 2: Preserve the test boot flags.** `application-test.properties` sets `stripe.secret.key`, `eureka.client.enabled=false`, `spring.cloud.*` disable flags, and `spring.cloud.compatibility-verifier.enabled=false` — these are **not** H2-related and the test context needs them. Keep the file as-is (it is already a properties file with no H2 reference), OR rename to `application-test.yml` with equivalent YAML. **Do not delete these flags.** (H2 removal is only the pom dependency + the `Auditable`/Testcontainers path already avoids H2.)

- [ ] **Step 3: Replace the reactive circuit-breaker starter** in `payment/pom.xml`:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
```
(replacing `spring-cloud-starter-circuitbreaker-reactor-resilience4j`).

- [ ] **Step 4: Verify the circuit breaker still resolves.** `ResilientOpaqueTokenIntrospector` uses `io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry` (core, non-reactive) — confirm it still compiles and the `management.health.circuitbreakers.enabled` health contributor resolves.

Run: `./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS. If the context fails to start for a missing reactive type, inspect the actual usage and adjust (the introspector should not need the reactive API).

- [ ] **Step 5: Tidy `application.yml`** — remove the dead `logging.level.blog: WARN` line; add an explicit Redis block under `spring:`:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```
Leave the OTLP tracing/metrics config in place. Remove `spring.zipkin.base-url` **only if** no Zipkin exporter is configured (the OTLP exporters are the live path); if unsure, leave it and note it — do not break tracing.

- [ ] **Step 6: Full verify + commit**

Run: `./mvnw -f payment/pom.xml clean verify` → BUILD SUCCESS.
```bash
git add payment/pom.xml payment/src/main/resources/application.yml payment/src/test/resources/
git commit -m "chore(payment): drop H2, use non-reactive resilience4j, tidy config"
```

---

## Task 6: JaCoCo coverage gate

**Files:**
- Modify: `payment/pom.xml`

**Interfaces:** none (build gate only).

- [ ] **Step 1: Add the JaCoCo plugin** to `payment/pom.xml` `<build><plugins>`, matching the sibling modules' gate (80% line overall; 100% line on the `domain` model). Use `prepare-agent` + a `check` execution bound to `verify` with two rules: a global `BUNDLE` line ratio ≥ 0.80 and a per-class rule limited to `com.ganchevdimitarg.payment.domain.*` at line ratio 1.00. (Read `catalog/pom.xml` or `order/pom.xml` for the exact `jacoco-maven-plugin` block already used in this repo and copy its shape; keep the version BOM-managed or matching the sibling.)

- [ ] **Step 2: Run verify — expect the gate to evaluate**

Run: `./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS with the JaCoCo `check` passing. If line coverage is below 80%, the missing coverage is added by Tasks 7–8 (idempotency IT, gateway test, security/envelope tests) — if this task alone cannot reach 80%, sequence its `check`-enforcement commit *after* Task 8, but add the plugin (report + agent) here. State which in the commit.

- [ ] **Step 3: Commit**

```bash
git add payment/pom.xml
git commit -m "test(payment): add JaCoCo coverage gate"
```

---

## Task 7: maven-failsafe binding + Redis Testcontainer + idempotency IT

**Files:**
- Modify: `payment/pom.xml`
- Modify: `payment/src/test/java/com/ganchevdimitarg/payment/AbstractIntegrationTest.java`
- Create: `payment/src/test/java/com/ganchevdimitarg/payment/idempotency/IdempotencyInterceptorIT.java`

**Interfaces:**
- Produces: `*IT` classes run in `verify`; `AbstractIntegrationTest` provides Postgres **and** Redis via Testcontainers.
- Consumes: the existing `IdempotencyInterceptor` (`payment:idempotency:<key>`, 409 on duplicate, 400 on missing key).

- [ ] **Step 1: Add the maven-failsafe plugin** to `payment/pom.xml` `<build><plugins>` with `integration-test` + `verify` goals bound (default `*IT` include pattern). This makes `CustomerPersistenceIT` and the new `IdempotencyInterceptorIT` part of the `verify` gate.

- [ ] **Step 2: Add the Redis container to `AbstractIntegrationTest`** (mirror the `order` base):

```java
package com.ganchevdimitarg.payment;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

- [ ] **Step 3: Write the idempotency IT** (`IdempotencyInterceptorIT.java`) — drive real HTTP through the interceptor against Testcontainers Redis, using an authenticated write. Use `TestRestTemplate`/`MockMvc` with a `SCOPE_payment.write` JWT. Assertions: duplicate `Idempotency-Key` on a write → 409; absent key on a write → 400; a first write stores the Redis key. Test names `should_<behaviour>_when_<condition>`. Use a per-test unique key (`UUID.randomUUID()`), no `@AfterEach` cleanup needed (unique keys).

```java
package com.ganchevdimitarg.payment.idempotency;

import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full-context IT (@SpringBootTest via AbstractIntegrationTest). Inject MockMvc with Spring Security
// applied so the interceptor + resource-server chain run against real Testcontainers Redis.
class IdempotencyInterceptorIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc; // configure via @AutoConfigureMockMvc on the class if not auto-injected

    @Autowired
    StringRedisTemplate redis;

    @Test
    void should_return400_when_writeHasNoIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/payment/charge/create-charge")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_payment.write")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return409_when_idempotencyKeyReplayed() throws Exception {
        String key = UUID.randomUUID().toString();
        // First call stores the key (body invalid → downstream may 4xx, but the interceptor stores first).
        // To isolate the interceptor, assert on the SECOND call being 409 regardless of the first's downstream result.
        // ... perform first POST with the key, then second POST with the same key, expect 409.
    }
}
```
*(The implementer completes the second test's arrange block against the real endpoint. Add `@org.springframework.test.web.servlet.setup.*`/`@AutoConfigureMockMvc` as needed for MockMvc injection in a `@SpringBootTest`; apply `springSecurity()` so the resource-server chain and interceptor both run.)*

- [ ] **Step 4: Run the ITs — expect pass**

Run: `./mvnw -f payment/pom.xml verify`
Expected: failsafe runs `CustomerPersistenceIT` + `IdempotencyInterceptorIT`; all green.

- [ ] **Step 5: Commit**

```bash
git add payment/pom.xml \
        payment/src/test/java/com/ganchevdimitarg/payment/AbstractIntegrationTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/idempotency/IdempotencyInterceptorIT.java
git commit -m "test(payment): failsafe binding, Redis container, idempotency IT"
```

---

## Task 8: Gateway error-translation, security, and problem+json tests

**Files:**
- Create: `payment/src/test/java/com/ganchevdimitarg/payment/gateway/StripePaymentGatewayTest.java`
- Create: `payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeAuthorizationTest.java` (or an IT if method security needs a context)
- Create: `payment/src/test/java/com/ganchevdimitarg/payment/exception/ProblemJsonEnvelopeTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayException`, `ControllerExceptionHandler`, `@PreAuthorize("hasAuthority('SCOPE_payment.write')")` on the service.

- [ ] **Step 1: `StripePaymentGatewayTest`** — prove the adapter translates a `StripeException` to `PaymentGatewayException` and never leaks `com.stripe.*`. Use Mockito `mockStatic(Charge.class)` (Stripe's static `create`) to throw a `StripeException`, then assert `assertThatThrownBy(() -> gateway.createCharge(request, "k")).isInstanceOf(PaymentGatewayException.class)`. Instantiate `new StripePaymentGateway("sk_test_dummy")` and call `init()` (or set the field) before invoking. If static mocking of the Stripe SDK proves brittle, cover the translation via the `call(...)` helper by exercising one method that throws.

- [ ] **Step 2: Security regression** — a `SCOPE_payment.read`-only principal invoking a write method is denied. Prefer a `@SpringBootTest` IT (extends `AbstractIntegrationTest`) with `@WithMockUser(authorities = "SCOPE_payment.read")` calling `chargeService.createCharge(...)` and asserting `AccessDeniedException` (method security is active only in a Spring context, not in the plain-Mockito service unit test). Name: `should_denyWrite_when_callerHasReadScopeOnly`.

- [ ] **Step 3: Problem+json envelope** — assert a domain exception renders `application/problem+json` with `code` + `timestamp`. Drive `ControllerExceptionHandler` via a standalone MockMvc setup on a tiny throwaway controller, or via one of the real controllers with a mocked service throwing `NotFoundException`, asserting `status().isNotFound()`, `content().contentType("application/problem+json")`, `jsonPath("$.code")`, `jsonPath("$.timestamp")`.

- [ ] **Step 4: Run — expect pass**

Run: `./mvnw -f payment/pom.xml verify`
Expected: all green; JaCoCo line coverage now ≥ 80% (if Task 6 deferred `check`-enforcement, enable it now and re-run).

- [ ] **Step 5: Commit**

```bash
git add payment/src/test/java/com/ganchevdimitarg/payment/gateway/StripePaymentGatewayTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/service/impl/ChargeAuthorizationTest.java \
        payment/src/test/java/com/ganchevdimitarg/payment/exception/ProblemJsonEnvelopeTest.java
git commit -m "test(payment): gateway translation, method-security, problem+json envelope"
```

---

## Task 9: Decisions log + final verification

**Files:**
- Modify: `payment/decisions.md`

- [ ] **Step 1: Append a dated entry** to `payment/decisions.md`:

```markdown
- 2026-07-07 — Boot-4 Grade-A remediation. Committed the Boot-4 migration (security lambda
  DSL + dual JWT/opaque resolver, PaymentGateway port over Stripe, problem+json exception
  model, record DTOs, V2 audit columns + optimistic locking). Charge safety: the Stripe call
  runs OUTSIDE the DB transaction with a Stripe idempotency key (retry cannot double-charge);
  the local row is written by a separate @Transactional persistence bean (avoids the
  self-invocation proxy trap). Restored the full-refund endpoint (order's post-charge
  compensation saga depends on it). Removed H2 (Testcontainers-only), swapped the reactive
  resilience4j starter for the servlet one, added failsafe/JaCoCo gates and a Redis
  Testcontainer. Follow-up: HTTP idempotency remains store-key-only (409-on-duplicate),
  consistent with auth/order/catalog — a move to cached-response replay is repo-wide.
```

- [ ] **Step 2: Final gate**

Run: `./mvnw -f payment/pom.xml clean verify`
Expected: BUILD SUCCESS — unit + IT (failsafe) green, JaCoCo satisfied.

Run: `./mvnw -f payment/pom.xml checkstyle:check`
Expected: run for signal; if only pre-existing violations remain, do not block (per Environment Caveats).

- [ ] **Step 3: Commit**

```bash
git add payment/decisions.md
git commit -m "docs(payment): record Grade-A remediation decisions"
```

---

## Self-Review

- **Spec coverage:** Phase 0 → Task 0; Phase 1 (refund) → Task 1; Phase 2 (charge safety: Stripe key + outside-tx) → Task 2 (charge) + Task 3 (customer/card); Phase 3 (convention) → Task 4 (entities) + Task 5 (H2/reactor/yml); Phase 4 (testability) → Task 6 (JaCoCo) + Task 7 (failsafe/Redis/idempotency IT) + Task 8 (gateway/security/envelope); Phase 5 (docs/verify) → Task 9. Every spec section maps to a task.
- **Type consistency:** `refund(RefundChargeCommand) → ChargeResponse` (Task 1) used by service/controller/tests; `GatewayRefund(String id, String charge, String status)` produced by the adapter, consumed by the service; `findByChargeId → Optional<AppCharge>` everywhere (Task 1). `createCharge(CreateChargeCommand, String idempotencyKey)` consistent across port (`createCharge(ChargeRequest, String)`), service interface, impl, controller, and tests (Task 2); `createCustomer(..., String)` / `createCard(..., String)` consistent across port/service/controller/tests (Task 3). `ChargePersistence.persistCharge(GatewayCharge, AppCustomer)`, `CustomerPersistence.persistCustomer(GatewayCustomer) → CustomerResponse`, `CardPersistence.persistCard(GatewayCard, AppCustomer)` match their call sites.
- **Migration numbering:** no schema change is required (all changes are code/pom/config/test); existing max is V2, next free is V3 if ever needed.
- **Self-invocation trap:** Tasks 2 & 3 place every post-provider persistence step on a **separate** `@Transactional` collaborator bean, never an in-bean call — explicitly enforced and tested via `verify(...Persistence)`.
- **No reactive leak:** Task 5 removes the only reactive dependency; payment stays WebMVC.
- **Money-safety invariant:** no amount/balance cached; the only Redis payload is the idempotency dedupe marker (`payment:idempotency:<key>`, 24h TTL).
- **Ordering dependency:** Task 6 (JaCoCo) may need Task 7–8's coverage to pass the 80% gate; the task states the fallback (add plugin in 6, enforce `check` after 8) so no task is left red.
```
