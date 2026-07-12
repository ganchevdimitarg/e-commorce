# Security & Trust Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the payment `X-User-Id` spoofing gap and enforce the platform's "downstream trusts gateway headers" convention with a verifiable HMAC signature, without breaking the legitimate `order`→`payment` service-to-service charge delegation.

**Architecture:** `gateway` signs `X-User-Id`/`X-User-Roles` with a shared-secret HMAC-SHA256 plus a freshness timestamp before forwarding to any downstream service. A new shared verifier in the `client` module (plus a WebFlux-specific variant for the one reactive service, `profile`) rejects any request that carries an `X-User-Id` without a valid, fresh signature — but passes through unmodified any request that carries no `X-User-Id` claim at all, so existing service-to-service calls that don't rely on that header are unaffected. `order`'s outbound calls to `payment` forward the original signed headers unchanged via a `ClientHttpRequestInterceptor`, since the signature authenticates the original gateway-issued claim, not the immediate caller.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Security (servlet + WebFlux), `javax.crypto` (HMAC-SHA256), JUnit 5, Mockito, `MockRestServiceServer`.

## Global Constraints

- Java 25, Spring Boot 4.1.0 (root `pom.xml` baseline) — do not introduce any dependency version not already pinned in the root BOM.
- `@RequiredArgsConstructor` / constructor injection only — never field `@Autowired` (root CLAUDE.md).
- No raw `Optional.get()`; repositories unaffected here, not applicable to this plan's files.
- Test naming: `should_<expectedBehavior>_when_<condition>` (root CLAUDE.md).
- No `Thread.sleep()` in tests.
- Secrets via `${ENV_VAR}` only, never inline — the new shared secret is `${GATEWAY_TRUST_SECRET}`, referenced as Spring property `gateway.trust.secret`.
- **Build order:** `client` is a dependency of `catalog`, `order`, `payment`, `notification`, `profile`. After Task 1–2 change `client`, install it before building any dependent module: `./mvnw -f client/pom.xml install -DskipTests`.
- Do not touch `docs/decisions.md` or any currently git-staged-for-deletion file — that is being handled separately by the repo owner.

---

## Task 1: `client` — HMAC signature verifier (core logic, TDD)

**Files:**
- Modify: `client/pom.xml` (add test dependency)
- Create: `client/src/main/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifier.java`
- Test: `client/src/test/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifierTest.java`

**Interfaces:**
- Produces: `public class GatewaySignatureVerifier { public GatewaySignatureVerifier(String secret); public boolean isValid(String userId, String roles, String timestamp, String signature); public String sign(String userId, String roles, String timestamp); }` — consumed by Task 2 (`client`) and mirrored (signing side only) by Task 3 (`gateway`).

- [ ] **Step 1: Add test dependencies to `client/pom.xml`**

`client` currently has no test dependencies at all. Add, immediately before the closing `</dependencies>` tag (after the `lombok` dependency, line 31 of the current file):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing test**

Create `client/src/test/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifierTest.java`:

```java
package com.ganchevdimitarg.client.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySignatureVerifierTest {

    private static final String SECRET = "test-shared-secret";
    private final GatewaySignatureVerifier verifier = new GatewaySignatureVerifier(SECRET);

    @Test
    void should_acceptSignature_when_freshAndCorrect() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        assertThat(verifier.isValid("user-1", "ROLE_USER", timestamp, signature)).isTrue();
    }

    @Test
    void should_rejectSignature_when_tampered() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        assertThat(verifier.isValid("user-2", "ROLE_USER", timestamp, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_expired() {
        String staleTimestamp = String.valueOf(System.currentTimeMillis() - 31_000L);
        String signature = verifier.sign("user-1", "ROLE_USER", staleTimestamp);

        assertThat(verifier.isValid("user-1", "ROLE_USER", staleTimestamp, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_missing() {
        assertThat(verifier.isValid("user-1", "ROLE_USER", "123", null)).isFalse();
    }

    @Test
    void should_rejectSignature_when_timestampMissing() {
        String signature = verifier.sign("user-1", "ROLE_USER", "123");

        assertThat(verifier.isValid("user-1", "ROLE_USER", null, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_timestampNotNumeric() {
        assertThat(verifier.isValid("user-1", "ROLE_USER", "not-a-number", "anything")).isFalse();
    }

    @Test
    void should_produceSameSignature_when_rolesIsNull() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", null, timestamp);

        assertThat(verifier.isValid("user-1", null, timestamp, signature)).isTrue();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -f client/pom.xml test -Dtest=GatewaySignatureVerifierTest`
Expected: FAIL — compilation error, `GatewaySignatureVerifier` does not exist.

- [ ] **Step 4: Write minimal implementation**

Create `client/src/main/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifier.java`:

```java
package com.ganchevdimitarg.client.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies that {@code X-User-Id}/{@code X-User-Roles} were signed by the gateway
 * within the last 30 seconds, closing the header-spoofing gap without requiring
 * every downstream service to re-implement HMAC computation.
 */
public class GatewaySignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long MAX_AGE_MILLIS = 30_000L;

    private final SecretKeySpec key;

    public GatewaySignatureVerifier(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public boolean isValid(String userId, String roles, String timestamp, String signature) {
        if (timestamp == null || signature == null) {
            return false;
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > MAX_AGE_MILLIS) {
            return false;
        }
        String expected = sign(userId, roles, timestamp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public String sign(String userId, String roles, String timestamp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String payload = userId + "|" + (roles == null ? "" : roles) + "|" + timestamp;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute gateway trust signature", e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -f client/pom.xml test -Dtest=GatewaySignatureVerifierTest`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add client/pom.xml client/src/main/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifier.java client/src/test/java/com/ganchevdimitarg/client/security/GatewaySignatureVerifierTest.java
git commit -m "feat(client): add gateway trust signature verifier"
```

---

## Task 2: `client` — servlet trust filter (TDD)

**Files:**
- Create: `client/src/main/java/com/ganchevdimitarg/client/security/GatewayTrustFilter.java`
- Test: `client/src/test/java/com/ganchevdimitarg/client/security/GatewayTrustFilterTest.java`

**Interfaces:**
- Consumes: `GatewaySignatureVerifier` (Task 1) — `boolean isValid(String, String, String, String)`.
- Produces: `public class GatewayTrustFilter extends OncePerRequestFilter { public GatewayTrustFilter(GatewaySignatureVerifier verifier); }` — consumed by Task 4 (catalog/order/payment/notification `ResourceServerConfig`).

- [ ] **Step 1: Write the failing test**

Create `client/src/test/java/com/ganchevdimitarg/client/security/GatewayTrustFilterTest.java`:

```java
package com.ganchevdimitarg.client.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GatewayTrustFilterTest {

    private static final String SECRET = "test-shared-secret";

    private GatewaySignatureVerifier verifier;
    private GatewayTrustFilter filter;

    @BeforeEach
    void setUp() {
        verifier = new GatewaySignatureVerifier(SECRET);
        filter = new GatewayTrustFilter(verifier);
    }

    @Test
    void should_passThrough_when_noUserIdHeaderPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payment/customer/get-customer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_allowRequest_when_signatureValidAndFresh() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Roles", "ROLE_USER");
        request.addHeader("X-Gateway-Timestamp", timestamp);
        request.addHeader("X-Gateway-Signature", signature);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_reject401_when_userIdPresentButSignatureMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "attacker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        verifyNoInteractions(chain);
    }

    @Test
    void should_reject401_when_signatureInvalid() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payment/charge/create-charge");
        request.addHeader("X-User-Id", "attacker");
        request.addHeader("X-Gateway-Timestamp", timestamp);
        request.addHeader("X-Gateway-Signature", "forged-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f client/pom.xml test -Dtest=GatewayTrustFilterTest`
Expected: FAIL — compilation error, `GatewayTrustFilter` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `client/src/main/java/com/ganchevdimitarg/client/security/GatewayTrustFilter.java`:

```java
package com.ganchevdimitarg.client.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects any request that claims an {@code X-User-Id} without a valid, fresh
 * gateway signature. A request with no {@code X-User-Id} at all makes no identity
 * claim and passes through unchanged — this keeps existing service-to-service
 * calls that never set the header (e.g. a plain username lookup) working.
 */
public class GatewayTrustFilter extends OncePerRequestFilter {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLES = "X-User-Roles";
    private static final String TIMESTAMP = "X-Gateway-Timestamp";
    private static final String SIGNATURE = "X-Gateway-Signature";

    private final GatewaySignatureVerifier verifier;

    public GatewayTrustFilter(GatewaySignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID);
        if (userId == null || userId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String roles = request.getHeader(USER_ROLES);
        String timestamp = request.getHeader(TIMESTAMP);
        String signature = request.getHeader(SIGNATURE);

        if (!verifier.isValid(userId, roles, timestamp, signature)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,\
                    "detail":"Missing or invalid gateway trust signature"}""");
            return;
        }

        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -f client/pom.xml test -Dtest=GatewayTrustFilterTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Install `client` so dependent modules pick up the new classes**

Run: `./mvnw -f client/pom.xml install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/ganchevdimitarg/client/security/GatewayTrustFilter.java client/src/test/java/com/ganchevdimitarg/client/security/GatewayTrustFilterTest.java
git commit -m "feat(client): add servlet filter enforcing gateway trust signature"
```

---

## Task 3: `gateway` — sign identity headers (TDD, modifies existing filter)

**Files:**
- Create: `gateway/src/main/java/com/ganchevdimitarg/gateway/filter/GatewaySignatureSigner.java`
- Modify: `gateway/src/main/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilter.java`
- Modify: `gateway/src/test/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilterTest.java`
- Modify: `gateway/src/main/resources/application-dev.yml`

**Interfaces:**
- Produces: `public class GatewaySignatureSigner { public GatewaySignatureSigner(String secret); public String sign(String userId, String roles, String timestamp); }`. This intentionally duplicates `client`'s signing algorithm rather than adding `client` as a dependency of `gateway` — `client` pulls in `spring-boot-starter-web`, which conflicts with `gateway`'s WebFlux stack (Spring Boot does not support both servlet and reactive web starters on one classpath cleanly). Both implementations must compute byte-identical HMAC output for the same inputs; if you change one, change the other.

**Note on why gateway doesn't reuse `client`:** `gateway` is WebFlux-only and has zero internal module dependencies today (verified: its `pom.xml` declares no `com.ganchevdimitarg` artifacts). Adding `client` would transitively add `spring-boot-starter-web` (servlet/Tomcat) to a reactive Netty-based module — Spring Boot autoconfiguration is not designed for both stacks coexisting. The signer is ~15 lines; duplicating it is cheaper than the alternative.

- [ ] **Step 1: Write the failing test for the signer**

Create `gateway/src/test/java/com/ganchevdimitarg/gateway/filter/GatewaySignatureSignerTest.java`:

```java
package com.ganchevdimitarg.gateway.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySignatureSignerTest {

    @Test
    void should_produceSameSignature_when_calledTwiceWithSameInputs() {
        GatewaySignatureSigner signer = new GatewaySignatureSigner("test-shared-secret");

        String first = signer.sign("user-1", "ROLE_USER", "1000");
        String second = signer.sign("user-1", "ROLE_USER", "1000");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_produceDifferentSignature_when_userIdDiffers() {
        GatewaySignatureSigner signer = new GatewaySignatureSigner("test-shared-secret");

        assertThat(signer.sign("user-1", "ROLE_USER", "1000"))
                .isNotEqualTo(signer.sign("user-2", "ROLE_USER", "1000"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f gateway/pom.xml test -Dtest=GatewaySignatureSignerTest`
Expected: FAIL — `GatewaySignatureSigner` does not exist.

- [ ] **Step 3: Write the signer**

Create `gateway/src/main/java/com/ganchevdimitarg/gateway/filter/GatewaySignatureSigner.java`:

```java
package com.ganchevdimitarg.gateway.filter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signs the identity headers gateway injects downstream. Must stay byte-for-byte
 * identical to {@code com.ganchevdimitarg.client.security.GatewaySignatureVerifier#sign}
 * — duplicated here because gateway (WebFlux) cannot depend on client
 * (pulls in spring-boot-starter-web).
 */
public class GatewaySignatureSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public GatewaySignatureSigner(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String userId, String roles, String timestamp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String payload = userId + "|" + (roles == null ? "" : roles) + "|" + timestamp;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute gateway trust signature", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify the signer passes**

Run: `./mvnw -f gateway/pom.xml test -Dtest=GatewaySignatureSignerTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Update the failing `UserIdentityGlobalFilterTest` expectations first**

Replace the entire content of `gateway/src/test/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilterTest.java`:

```java
package com.ganchevdimitarg.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityGlobalFilterTest {

    private static final String SECRET = "test-shared-secret";
    private final UserIdentityGlobalFilter filter = new UserIdentityGlobalFilter(SECRET);

    @Test
    void should_stripSpoofedIdentityHeaders_when_requestIsUnauthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/items")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker")
                        .header(UserIdentityGlobalFilter.USER_ROLES, "ROLE_ADMIN"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ID)).isNull();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ROLES)).isNull();
    }

    @Test
    void should_overwriteSpoofedHeadersWithSignedPrincipal_when_authenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/order")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user-123", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .verifyComplete();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ID)).isEqualTo("user-123");
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ROLES)).isEqualTo("ROLE_USER");
        assertThat(headers.getFirst("X-Gateway-Timestamp")).isNotBlank();
        String timestamp = headers.getFirst("X-Gateway-Timestamp");
        String expectedSignature = new GatewaySignatureSigner(SECRET).sign("user-123", "ROLE_USER", timestamp);
        assertThat(headers.getFirst("X-Gateway-Signature")).isEqualTo(expectedSignature);
    }

    @Test
    void should_stripHeaders_when_authenticationIsAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/items")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymous)))
                .verifyComplete();

        assertThat(forwarded.get().getHeaders().getFirst(UserIdentityGlobalFilter.USER_ID)).isNull();
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./mvnw -f gateway/pom.xml test -Dtest=UserIdentityGlobalFilterTest`
Expected: FAIL — `UserIdentityGlobalFilter(String)` constructor does not exist yet.

- [ ] **Step 7: Update `UserIdentityGlobalFilter` to sign the headers**

Replace the entire content of `gateway/src/main/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilter.java`:

```java
package com.ganchevdimitarg.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Establishes the trusted identity contract with downstream services.
 *
 * <p>Downstream services trust {@code X-User-Id} / {@code X-User-Roles} only when
 * they carry a fresh HMAC signature computed here, so the gateway must be the sole
 * source of those headers. This filter therefore always <em>strips</em> any
 * client-supplied identity headers (closing the spoofing hole) and, for an
 * authenticated exchange, re-injects them from the OAuth2 principal along with a
 * signature and timestamp downstream services verify via
 * {@code GatewayTrustFilter} (client module) / {@code GatewayTrustWebFilter} (profile).
 */
@Component
public class UserIdentityGlobalFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USER_ROLES = "X-User-Roles";
    static final String GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";
    static final String GATEWAY_SIGNATURE = "X-Gateway-Signature";

    private final GatewaySignatureSigner signer;

    public UserIdentityGlobalFilter(@Value("${gateway.trust.secret}") String trustSecret) {
        this.signer = new GatewaySignatureSigner(trustSecret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLES);
                    h.remove(GATEWAY_TIMESTAMP);
                    h.remove(GATEWAY_SIGNATURE);
                }))
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
                .map(auth -> withIdentityHeaders(stripped, auth))
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, Authentication auth) {
        String userId = resolveUserId(auth);
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = signer.sign(userId, roles, timestamp);

        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set(USER_ID, userId);
                    if (!roles.isBlank()) {
                        h.set(USER_ROLES, roles);
                    }
                    h.set(GATEWAY_TIMESTAMP, timestamp);
                    h.set(GATEWAY_SIGNATURE, signature);
                }))
                .build();
    }

    private String resolveUserId(Authentication auth) {
        return switch (auth.getPrincipal()) {
            case OidcUser oidc -> oidc.getSubject();
            case OAuth2User oauth2 -> oauth2.getName();
            default -> auth.getName();
        };
    }

    @Override
    public int getOrder() {
        // Run early so downstream-facing filters (e.g. TokenRelay) see the trusted headers.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw -f gateway/pom.xml test -Dtest=UserIdentityGlobalFilterTest,GatewaySignatureSignerTest`
Expected: PASS (5 tests)

- [ ] **Step 9: Add the trust secret property**

In `gateway/src/main/resources/application-dev.yml`, add at the top level (anywhere outside the `spring:` block, e.g. after the `server:` block):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 10: Run the full gateway test suite**

Run: `./mvnw -f gateway/pom.xml test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 11: Commit**

```bash
git add gateway/src/main/java/com/ganchevdimitarg/gateway/filter/GatewaySignatureSigner.java gateway/src/main/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilter.java gateway/src/test/java/com/ganchevdimitarg/gateway/filter/GatewaySignatureSignerTest.java gateway/src/test/java/com/ganchevdimitarg/gateway/filter/UserIdentityGlobalFilterTest.java gateway/src/main/resources/application-dev.yml
git commit -m "feat(gateway): sign X-User-Id/X-User-Roles with HMAC before forwarding"
```

---

## Task 4: register `GatewayTrustFilter` in catalog, order, payment, notification

**Files:**
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/config/ResourceServerConfig.java`
- Modify: `order/src/main/java/com/ganchevdimitarg/order/config/ResourceServerConfig.java`
- Modify: `payment/src/main/java/com/ganchevdimitarg/payment/config/ResourceServerConfig.java`
- Modify: `notification/src/main/java/com/ganchevdimitarg/notification/config/ResourceServerConfig.java` (also fixes the null-check bug in the same file, per the review's Category 13 finding)
- Modify: `catalog/src/main/resources/application-dev.yml`, `order/src/main/resources/application-dev.yml`, `payment/src/main/resources/application-dev.yml`, `notification/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: `com.ganchevdimitarg.client.security.GatewayTrustFilter` (Task 2), `com.ganchevdimitarg.client.security.GatewaySignatureVerifier` (Task 1).

This is a wiring-only task — the filter logic itself is already unit-tested in Task 2, so no new tests are added here; verification is each module's existing test suite staying green plus a full `clean verify`.

- [ ] **Step 1: catalog — add the filter bean and registration**

In `catalog/src/main/java/com/ganchevdimitarg/catalog/config/ResourceServerConfig.java`, add imports after line 8 (`import java.util.Base64;`):

```java
import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import com.ganchevdimitarg.client.security.GatewayTrustFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
```

Change the `securityFilterChain` method (currently lines 38–56) to add the filter and take it as a parameter:

```java
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationManagerResolver<HttpServletRequest> resolver,
                                            GatewayTrustFilter gatewayTrustFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayTrustFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    GatewayTrustFilter gatewayTrustFilter(@Value("${gateway.trust.secret}") String trustSecret) {
        return new GatewayTrustFilter(new GatewaySignatureVerifier(trustSecret));
    }
```

Add to `catalog/src/main/resources/application-dev.yml` (top level, alongside other non-`spring` keys):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 2: order — same pattern**

In `order/src/main/java/com/ganchevdimitarg/order/config/ResourceServerConfig.java`, add imports after line 5 (`import com.ganchevdimitarg.order.exception.ProblemAuthenticationEntryPoint;`):

```java
import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import com.ganchevdimitarg.client.security.GatewayTrustFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
```

Change the `securityFilterChain` method (currently lines 37–53):

```java
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayTrustFilter gatewayTrustFilter) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayTrustFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(this.tokenAuthenticationManagerResolver()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    GatewayTrustFilter gatewayTrustFilter(@Value("${gateway.trust.secret}") String trustSecret) {
        return new GatewayTrustFilter(new GatewaySignatureVerifier(trustSecret));
    }
```

Add to `order/src/main/resources/application-dev.yml` (top level):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 3: payment — same pattern**

In `payment/src/main/java/com/ganchevdimitarg/payment/config/ResourceServerConfig.java`, add imports after line 4 (`import com.ganchevdimitarg.payment.exception.ProblemAuthenticationEntryPoint;`):

```java
import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import com.ganchevdimitarg.client.security.GatewayTrustFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
```

(`org.springframework.beans.factory.annotation.Value` is already imported in this file.)

Change the `securityFilterChain` method (currently lines 46–64):

```java
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationManagerResolver<HttpServletRequest> resolver,
                                            GatewayTrustFilter gatewayTrustFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayTrustFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    GatewayTrustFilter gatewayTrustFilter(@Value("${gateway.trust.secret}") String trustSecret) {
        return new GatewayTrustFilter(new GatewaySignatureVerifier(trustSecret));
    }
```

Add to `payment/src/main/resources/application-dev.yml` (top level):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 4: notification — same pattern, plus the null-check fix**

In `notification/src/main/java/com/ganchevdimitarg/notification/config/ResourceServerConfig.java`, add imports after line 2 (`import com.ganchevdimitarg.client.introspector.CustomOpaqueTokenIntrospector;`):

```java
import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import com.ganchevdimitarg.client.security.GatewayTrustFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
```

Change `securityFilterChain` (currently lines 35–49):

```java
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayTrustFilter gatewayTrustFilter) {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/notification/**").hasAuthority("SCOPE_notification.write")
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayTrustFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(this.tokenAuthenticationManagerResolver()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    GatewayTrustFilter gatewayTrustFilter(@Value("${gateway.trust.secret}") String trustSecret) {
        return new GatewayTrustFilter(new GatewaySignatureVerifier(trustSecret));
    }
```

Fix the null-check bug in the same file — replace `isJwt` (currently lines 64–75):

```java
    private boolean isJwt(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        try {
            jwtDecoder.decode(authorization.replace("Bearer ", ""));
            return true;
        } catch (BadJwtException e) {
            log.debug(e.getMessage());
            return false;
        }
    }
```

Add to `notification/src/main/resources/application-dev.yml` (top level):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 5: Add a unit test for notification's null-check fix**

Check for an existing `ResourceServerConfigTest` first: if `notification/src/test/java/com/ganchevdimitarg/notification/config/` has no test for `isJwt`, create `notification/src/test/java/com/ganchevdimitarg/notification/config/ResourceServerConfigTest.java`:

```java
package com.ganchevdimitarg.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServerConfigTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private HttpServletRequest request;

    @Test
    void should_returnFalse_when_authorizationHeaderMissing() {
        when(request.getHeader("Authorization")).thenReturn(null);

        ResourceServerConfig config = new ResourceServerConfig(jwtDecoder, null, null);
        boolean result = ReflectionTestUtils.invokeMethod(config, "isJwt", request);

        assertThat(result).isFalse();
    }

    @Test
    void should_returnFalse_when_authorizationHeaderBlank() {
        when(request.getHeader("Authorization")).thenReturn("   ");

        ResourceServerConfig config = new ResourceServerConfig(jwtDecoder, null, null);
        boolean result = ReflectionTestUtils.invokeMethod(config, "isJwt", request);

        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 6: Run each module's test suite**

Run: `./mvnw -f catalog/pom.xml test`
Run: `./mvnw -f order/pom.xml test`
Run: `./mvnw -f payment/pom.xml test`
Run: `./mvnw -f notification/pom.xml test`
Expected: BUILD SUCCESS for all four.

- [ ] **Step 7: Commit**

```bash
git add catalog/src/main/java/com/ganchevdimitarg/catalog/config/ResourceServerConfig.java catalog/src/main/resources/application-dev.yml order/src/main/java/com/ganchevdimitarg/order/config/ResourceServerConfig.java order/src/main/resources/application-dev.yml payment/src/main/java/com/ganchevdimitarg/payment/config/ResourceServerConfig.java payment/src/main/resources/application-dev.yml notification/src/main/java/com/ganchevdimitarg/notification/config/ResourceServerConfig.java notification/src/main/resources/application-dev.yml notification/src/test/java/com/ganchevdimitarg/notification/config/ResourceServerConfigTest.java
git commit -m "feat(security): enforce gateway trust signature and fix notification NPE"
```

---

## Task 5: `profile` — reactive trust filter (TDD) + `@PreAuthorize` relocation

**Files:**
- Create: `profile/src/main/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilter.java`
- Test: `profile/src/test/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilterTest.java`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/config/ResourceServerConfig.java`
- Modify: `profile/src/main/resources/application-dev.yml`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java`
- Test: `profile/src/test/java/com/ganchevdimitarg/profile/service/ProfileServiceSecurityTest.java`

**Interfaces:**
- Consumes: `com.ganchevdimitarg.client.security.GatewaySignatureVerifier` (Task 1) — `profile` already depends on `client`, and this class has zero servlet-specific imports, so it's safe to reuse directly (unlike `GatewayTrustFilter`, which is servlet-specific).
- Produces: `public class GatewayTrustWebFilter implements WebFilter`.

- [ ] **Step 1: Write the failing test**

Create `profile/src/test/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilterTest.java`:

```java
package com.ganchevdimitarg.profile.security;

import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTrustWebFilterTest {

    private static final String SECRET = "test-shared-secret";
    private final GatewaySignatureVerifier verifier = new GatewaySignatureVerifier(SECRET);
    private final GatewayTrustWebFilter filter = new GatewayTrustWebFilter(verifier);

    @Test
    void should_passThrough_when_noUserIdHeaderPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void should_allowRequest_when_signatureValidAndFresh() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Roles", "ROLE_USER")
                        .header("X-Gateway-Timestamp", timestamp)
                        .header("X-Gateway-Signature", signature));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void should_reject401_when_signatureMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me")
                        .header("X-User-Id", "attacker"));
        WebFilterChain chain = ex -> Mono.error(new AssertionError("chain must not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f profile/pom.xml test -Dtest=GatewayTrustWebFilterTest`
Expected: FAIL — `GatewayTrustWebFilter` does not exist.

- [ ] **Step 3: Write the filter**

Create `profile/src/main/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilter.java`:

```java
package com.ganchevdimitarg.profile.security;

import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive counterpart to {@code client}'s {@code GatewayTrustFilter} — profile is
 * the platform's one WebFlux business service, so it needs its own {@link WebFilter}
 * rather than the shared servlet {@code OncePerRequestFilter}. Delegates to the same
 * {@link GatewaySignatureVerifier} used by every servlet-based service.
 */
@Component
public class GatewayTrustWebFilter implements WebFilter {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLES = "X-User-Roles";
    private static final String TIMESTAMP = "X-Gateway-Timestamp";
    private static final String SIGNATURE = "X-Gateway-Signature";

    private final GatewaySignatureVerifier verifier;

    public GatewayTrustWebFilter(GatewaySignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(USER_ID);
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }

        String roles = request.getHeaders().getFirst(USER_ROLES);
        String timestamp = request.getHeaders().getFirst(TIMESTAMP);
        String signature = request.getHeaders().getFirst(SIGNATURE);

        if (!verifier.isValid(userId, roles, timestamp, signature)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add("Content-Type", "application/problem+json");
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -f profile/pom.xml test -Dtest=GatewayTrustWebFilterTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Register the filter and the trust-secret bean**

In `profile/src/main/java/com/ganchevdimitarg/profile/config/ResourceServerConfig.java`, add an import after line 5 (`import com.ganchevdimitarg.profile.handler.CustomLogoutHandler;`):

```java
import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import com.ganchevdimitarg.profile.security.GatewayTrustWebFilter;
```

Add a bean factory method (after the `jwtDecoder()` bean, currently lines 37–40) and wire the filter into `securityWebFilterChain` (currently lines 59–89):

```java
    @Bean
    public GatewayTrustWebFilter gatewayTrustWebFilter(
            @Value("${gateway.trust.secret}") String trustSecret) {
        return new GatewayTrustWebFilter(new GatewaySignatureVerifier(trustSecret));
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                          GatewayTrustWebFilter gatewayTrustWebFilter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .addFilterBefore(gatewayTrustWebFilter, org.springframework.security.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
                .logout(logout -> logout
                        .logoutUrl("/api/v1/profile/logout")
                        .logoutHandler(logoutHandler)
                        .logoutSuccessHandler(new HttpStatusReturningServerLogoutSuccessHandler())
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tokenAuthenticationManagerResolver())
                )
                .build();
    }
```

Note: `ServerHttpSecurity`'s `addFilterBefore` takes a `SecurityWebFiltersOrder` enum value, not a filter class — use `SecurityWebFiltersOrder.AUTHENTICATION` so the trust check runs before Spring Security attempts to authenticate the JWT.

Add to `profile/src/main/resources/application-dev.yml` (top level):

```yaml
gateway:
  trust:
    secret: ${GATEWAY_TRUST_SECRET}
```

- [ ] **Step 6: Run profile's test suite**

Run: `./mvnw -f profile/pom.xml test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Write the failing test for service-layer `@PreAuthorize`**

**Important — do not do a pure relocation.** `profile/src/test/java/com/ganchevdimitarg/profile/controller/ProfileControllerIntegrationTest.java:41-42` uses `@MockitoBean private ProfileService profileService`, which replaces the entire service bean with a raw Mockito mock — no Spring Security method-security advice is ever woven around it. The existing test `insufficientAuthority_Returns403` (lines 150-159) passes today only because `@PreAuthorize` is on the *controller* (`ProfileController.updateMe`), intercepting the call before it reaches the mock. If `@PreAuthorize` is removed from the controller, that same test would instead invoke the unstubbed mock (`profileService.updateProfile(...)` returns `null`, not `Mono`), producing a 500 instead of the expected 403 — a real regression. `order/src/main/java/com/ganchevdimitarg/order/service/OrderService.java:14-32` has `@PreAuthorize` on the interface with no equivalent HTTP-level test, so it has no such coupling — `profile` is the one module where this matters.

The correct fix is **defense-in-depth: add `@PreAuthorize` to the service interface in addition to the existing controller annotation**, not instead of it. This still closes the review's Category 14 concern (a caller invoking `ProfileService` directly, bypassing the controller, is now also blocked) with zero risk to existing coverage.

Create `profile/src/test/java/com/ganchevdimitarg/profile/service/ProfileServiceSecurityTest.java`:

```java
package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileServiceSecurityTest {

    @Test
    void should_requireProfileWriteScope_when_updatingProfile() throws NoSuchMethodException {
        var method = ProfileService.class.getMethod("updateProfile", String.class, UpdateProfileCommand.class);
        var preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')");
    }

    @Test
    void should_requireProfileWriteScope_when_settingUpPayment() throws NoSuchMethodException {
        var method = ProfileService.class.getMethod("setupPayment", String.class, CardSetupCommand.class);
        var preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')");
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `./mvnw -f profile/pom.xml test -Dtest=ProfileServiceSecurityTest`
Expected: FAIL — `@PreAuthorize` not present on the interface methods yet.

- [ ] **Step 9: Add `@PreAuthorize` to the service interface (controller keeps its own)**

Leave `profile/src/main/java/com/ganchevdimitarg/profile/controller/ProfileController.java` unchanged — its `@PreAuthorize` on `updateMe` (line 81) and `setupPayment` (line 106) stays exactly as-is.

In `profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java`, add `@PreAuthorize` to the `updateProfile` and `setupPayment` declarations (confirmed exact current signatures — lines 32 and 37):

```java
    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    Mono<Void> updateProfile(String userId, UpdateProfileCommand command);

    @PreAuthorize("hasAnyAuthority('SCOPE_profile.write', 'ROLE_USER')")
    Mono<UserDto> setupPayment(String userId, CardSetupCommand command);
```

Add the import to `ProfileService.java`: `import org.springframework.security.access.prepost.PreAuthorize;`

- [ ] **Step 10: Run test to verify it passes**

Run: `./mvnw -f profile/pom.xml test -Dtest=ProfileServiceSecurityTest`
Expected: PASS (2 tests)

- [ ] **Step 11: Run profile's full test suite**

Run: `./mvnw -f profile/pom.xml test`
Expected: BUILD SUCCESS — `ProfileControllerIntegrationTest.insufficientAuthority_Returns403` must still pass unchanged (controller annotation untouched), proving no regression.

- [ ] **Step 12: Commit**

```bash
git add profile/src/main/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilter.java profile/src/test/java/com/ganchevdimitarg/profile/security/GatewayTrustWebFilterTest.java profile/src/main/java/com/ganchevdimitarg/profile/config/ResourceServerConfig.java profile/src/main/resources/application-dev.yml profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java profile/src/test/java/com/ganchevdimitarg/profile/service/ProfileServiceSecurityTest.java
git commit -m "feat(profile): add reactive gateway trust filter and service-layer method security"
```

---

## Task 6: `order` — forward gateway-signed headers to `payment` (TDD)

**Files:**
- Create: `order/src/main/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptor.java`
- Test: `order/src/test/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptorTest.java`
- Modify: `order/src/main/java/com/ganchevdimitarg/order/config/RestClientConfig.java`

**Interfaces:**
- Produces: `public class GatewayHeaderPropagationInterceptor implements ClientHttpRequestInterceptor` — registered on `order`'s outbound `RestClient` bean.

**Why an interceptor, not per-call header-setting:** the signature authenticates the *original* gateway-issued claim, not `order`'s own identity, so it must be copied verbatim from the current inbound request onto every outbound call — reconstructing it from `Authentication.getName()` inside `ChargeServiceImpl` would produce an unsigned (and therefore rejected) header. An interceptor on the shared `RestClient` bean applies this uniformly to every outbound call `order` makes, with no per-call-site code needed.

- [ ] **Step 1: Write the failing test**

Create `order/src/test/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptorTest.java`:

```java
package com.ganchevdimitarg.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderPropagationInterceptorTest {

    private final GatewayHeaderPropagationInterceptor interceptor = new GatewayHeaderPropagationInterceptor();

    @Test
    void should_copyIdentityHeaders_when_presentOnInboundRequest() throws IOException {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader("X-User-Id", "user-1");
        inbound.addHeader("X-User-Roles", "ROLE_USER");
        inbound.addHeader("X-Gateway-Timestamp", "1000");
        inbound.addHeader("X-Gateway-Signature", "sig-abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        try {
            HttpRequest outbound = new MockClientHttpRequest();
            ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], 200);

            interceptor.intercept(outbound, new byte[0], execution);

            HttpHeaders headers = outbound.getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
            assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_USER");
            assertThat(headers.getFirst("X-Gateway-Timestamp")).isEqualTo("1000");
            assertThat(headers.getFirst("X-Gateway-Signature")).isEqualTo("sig-abc");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void should_notAddHeaders_when_noInboundRequestContext() throws IOException {
        RequestContextHolder.resetRequestAttributes();
        HttpRequest outbound = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], 200);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().getFirst("X-User-Id")).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f order/pom.xml test -Dtest=GatewayHeaderPropagationInterceptorTest`
Expected: FAIL — `GatewayHeaderPropagationInterceptor` does not exist.

- [ ] **Step 3: Write the interceptor**

Create `order/src/main/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptor.java`:

```java
package com.ganchevdimitarg.order.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Forwards the gateway-signed identity headers from the current inbound request onto
 * every outbound call order makes. The signature authenticates the original
 * gateway-issued claim, not order's own identity, so it must be copied unchanged
 * rather than regenerated — this is what lets payment trust order's delegated charge
 * requests without granting order (or anyone else) a blanket ability to spoof
 * X-User-Id.
 */
public class GatewayHeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    private static final String[] FORWARDED_HEADERS = {
            "X-User-Id", "X-User-Roles", "X-Gateway-Timestamp", "X-Gateway-Signature"
    };

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        HttpServletRequest inbound = currentInboundRequest();
        if (inbound != null) {
            for (String header : FORWARDED_HEADERS) {
                String value = inbound.getHeader(header);
                if (value != null) {
                    request.getHeaders().set(header, value);
                }
            }
        }
        return execution.execute(request, body);
    }

    private HttpServletRequest currentInboundRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -f order/pom.xml test -Dtest=GatewayHeaderPropagationInterceptorTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Register the interceptor on the RestClient bean**

In `order/src/main/java/com/ganchevdimitarg/order/config/RestClientConfig.java`, change the `restClient` bean method (currently lines 48–66):

```java
    @Bean
    RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        OAuth2ClientHttpRequestInterceptor requestInterceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        requestInterceptor.setClientRegistrationIdResolver(request -> defaultClientRegistrationId);

        // Explicit timeouts so a slow downstream cannot pin the calling thread — the
        // circuit breaker records the timeout instead of waiting indefinitely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(requestInterceptor)
                .requestInterceptor(new GatewayHeaderPropagationInterceptor())
                .build();
    }
```

- [ ] **Step 6: Run order's test suite**

Run: `./mvnw -f order/pom.xml test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add order/src/main/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptor.java order/src/test/java/com/ganchevdimitarg/order/config/GatewayHeaderPropagationInterceptorTest.java order/src/main/java/com/ganchevdimitarg/order/config/RestClientConfig.java
git commit -m "feat(order): forward gateway-signed identity headers to payment"
```

---

## Task 7: fix the `order`→`payment` charge/refund contract mismatch (prerequisite bug, TDD)

**Files:**
- Create: `order/src/main/java/com/ganchevdimitarg/order/dto/ChargeRequest.java`
- Create: `order/src/main/java/com/ganchevdimitarg/order/dto/RefundRequest.java`
- Create: `order/src/main/java/com/ganchevdimitarg/order/dto/PaymentChargeResponse.java`
- Modify: `order/src/main/java/com/ganchevdimitarg/order/service/ChargeService.java`
- Modify: `order/src/main/java/com/ganchevdimitarg/order/service/ChargeServiceImpl.java`
- Modify: `order/src/main/java/com/ganchevdimitarg/order/service/OrderServiceImpl.java`
- Modify: `order/src/test/java/com/ganchevdimitarg/order/service/ChargeServiceImplTest.java`

**Problem being fixed:** `order` currently posts its generic `PaymentDto` to `payment`'s `/api/v1/payment/charge/create-charge`, but that endpoint expects `CreateChargeCommand(orderId, cardId, amount, currency, receiptEmail)` — `orderId` is never sent (fails `@NotBlank` validation) and `payment` has no `fail-on-unknown-properties=false` override, so Jackson rejects `PaymentDto`'s extra fields outright. Neither call has ever worked against `payment`'s current (2026-07-08 Grade-A remediation) API shape.

**Interfaces:**
- Produces: `record ChargeRequest(String orderId, String cardId, long amount, String currency, String receiptEmail)`, `record RefundRequest(String chargeId)`, `record PaymentChargeResponse(String chargeId, String chargeStatus)`.
- Modifies: `ChargeService.makePayment(String cardId, String username, long amount, String orderId)` (adds `orderId` parameter) — the public `PaymentDto` return type of `makePayment`/`refund`/`saveCharge` is unchanged, so `OrderServiceImpl`'s use of `payment.chargeId()`/`payment.chargeStatus()` needs no further changes beyond the one new call-site argument.

- [ ] **Step 1: Update the failing tests first**

Replace the entire content of `order/src/test/java/com/ganchevdimitarg/order/service/ChargeServiceImplTest.java`:

```java
package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ChargeDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceImplTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock
    private ChargeDao chargeDao;
    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;

    private ChargeServiceImpl chargeService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        chargeService = new ChargeServiceImpl(chargeDao, builder.build(), circuitBreakerFactory);
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceGetCustomerByUsernameUri", "http://payment/customer?username=");
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceChargeCustomerUri", "http://payment/charge");
    }

    private void runSupplier() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Supplier<?> toRun = inv.getArgument(0);
            return toRun.get();
        });
    }

    @Test
    void should_saveCharge_when_paymentSucceeds() {
        Order order = Order.builder().username("john").orderNumber(1).build();
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        chargeService.saveCharge(order, payment);

        verify(chargeDao).save(any(Charge.class));
    }

    @Test
    void should_sendOrderIdAndReturnCharge_when_paymentServiceApproves() throws Exception {
        runSupplier();
        PaymentDto customer = PaymentDto.builder().username("john").customerId("cust_1").build();
        String chargeResponseJson = """
                {"chargeId":"ch_1","chargeStatus":"succeeded"}""";

        server.expect(requestTo("http://payment/customer?username=john"))
                .andExpect(method(GET))
                .andRespond(withSuccess(json.writeValueAsString(customer), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://payment/charge"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.orderId").value("42"))
                .andExpect(jsonPath("$.cardId").value("card_1"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.currency").value("usd"))
                .andRespond(withSuccess(chargeResponseJson, MediaType.APPLICATION_JSON));

        PaymentDto result = chargeService.makePayment("card_1", "john", 1000L, "42");

        assertThat(result.chargeId()).isEqualTo("ch_1");
        assertThat(result.chargeStatus()).isEqualTo("succeeded");
        server.verify();
    }

    @Test
    void should_returnRefund_when_paymentServiceRefundsInFull() throws Exception {
        runSupplier();
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceRefundChargeUri", "http://payment/refund");
        String refundResponseJson = """
                {"chargeId":"ch_1","chargeStatus":"refunded"}""";

        server.expect(requestTo("http://payment/refund"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.chargeId").value("ch_1"))
                .andRespond(withSuccess(refundResponseJson, MediaType.APPLICATION_JSON));

        PaymentDto result = chargeService.refund("ch_1", "john");

        assertThat(result.chargeId()).isEqualTo("ch_1");
        assertThat(result.chargeStatus()).isEqualTo("refunded");
        server.verify();
    }

    @Test
    void should_throwServiceUnavailable_when_paymentServiceUnavailable() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Function<Throwable, ?> fallback = inv.getArgument(1);
            return fallback.apply(new RuntimeException("payment down"));
        });

        assertThatThrownBy(() -> chargeService.makePayment("card_1", "john", 1000L, "42"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f order/pom.xml test -Dtest=ChargeServiceImplTest`
Expected: FAIL — `makePayment` does not accept a 4th argument yet; compilation error.

- [ ] **Step 3: Create the wire-format DTOs**

Create `order/src/main/java/com/ganchevdimitarg/order/dto/ChargeRequest.java`:

```java
package com.ganchevdimitarg.order.dto;

/** Wire-format request body for payment's POST /api/v1/payment/charge/create-charge. */
public record ChargeRequest(String orderId, String cardId, long amount, String currency, String receiptEmail) {
}
```

Create `order/src/main/java/com/ganchevdimitarg/order/dto/RefundRequest.java`:

```java
package com.ganchevdimitarg.order.dto;

/** Wire-format request body for payment's POST /api/v1/payment/charge/refund-charge. */
public record RefundRequest(String chargeId) {
}
```

Create `order/src/main/java/com/ganchevdimitarg/order/dto/PaymentChargeResponse.java`:

```java
package com.ganchevdimitarg.order.dto;

/** Wire-format response from payment's charge/refund endpoints. */
public record PaymentChargeResponse(String chargeId, String chargeStatus) {
}
```

- [ ] **Step 4: Update the `ChargeService` interface**

Replace `order/src/main/java/com/ganchevdimitarg/order/service/ChargeService.java`:

```java
package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.PaymentDto;

public interface ChargeService {
    void saveCharge(Order order, PaymentDto paymentCharge);
    PaymentDto makePayment(String cardId, String username, long amount, String orderId);
    PaymentDto refund(String stripeChargeId, String username);
}
```

- [ ] **Step 5: Update `ChargeServiceImpl`**

Replace `order/src/main/java/com/ganchevdimitarg/order/service/ChargeServiceImpl.java`:

```java
package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ChargeDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.ChargeRequest;
import com.ganchevdimitarg.order.dto.PaymentChargeResponse;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.dto.RefundRequest;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServiceImpl implements ChargeService {

    private static final String PAYMENT_CIRCUIT_BREAKER = "order-payment";

    private final ChargeDao chargeDao;
    private final RestClient restClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${payment.service.customer.get.uri}")
    private String paymentServiceGetCustomerByUsernameUri;
    @Value("${payment.service.charge.post.uri}")
    private String paymentServiceChargeCustomerUri;
    @Value("${payment.service.charge.refund.post.uri}")
    private String paymentServiceRefundChargeUri;

    @Override
    public PaymentDto makePayment(String cardId, String authenticationName, long amount, String orderId) {
        PaymentDto paymentCustomer = getCustomerFromPaymentService(
                paymentServiceGetCustomerByUsernameUri + authenticationName
        );

        PaymentDto chargeCustomer = chargeCustomer(amount, paymentCustomer, cardId, orderId);
        log.info("Payment went through successfully: {}", chargeCustomer.chargeId());
        return chargeCustomer;
    }

    @Override
    public void saveCharge(Order order, PaymentDto paymentCharge) {
        Charge charge = Charge.builder()
                .chargeId(paymentCharge.chargeId())
                .status(paymentCharge.chargeStatus())
                .order(order)
                .build();

        chargeDao.save(charge);
        log.info("Charge was successfully created");
    }

    /**
     * Refund a charge in full. Compensation always returns the entire captured amount, so no
     * amount is sent — the payment service refunds the charge in full.
     */
    @Override
    public PaymentDto refund(String stripeChargeId, String username) {
        RefundRequest refundRequest = new RefundRequest(stripeChargeId);

        PaymentChargeResponse refunded = sendChargeRequestToPaymentService(paymentServiceRefundChargeUri, refundRequest);
        log.info("Refund went through successfully: {}", refunded.chargeId());
        return PaymentDto.builder().chargeId(refunded.chargeId()).chargeStatus(refunded.chargeStatus()).build();
    }

    private PaymentDto chargeCustomer(long amount, PaymentDto paymentCustomer, String cardId, String orderId) {
        ChargeRequest chargeRequest = new ChargeRequest(orderId, cardId, amount, "usd", paymentCustomer.username());

        PaymentChargeResponse response = sendChargeRequestToPaymentService(paymentServiceChargeCustomerUri, chargeRequest);
        return PaymentDto.builder().chargeId(response.chargeId()).chargeStatus(response.chargeStatus()).build();
    }

    /**
     * A tripped breaker or a failed call surfaces as a 503 {@link ServiceUnavailableException}
     * — never a silent empty sentinel that callers would mistake for a valid response.
     */
    private PaymentChargeResponse sendChargeRequestToPaymentService(String uri, Object request) {
        PaymentChargeResponse response = circuitBreakerFactory.create(PAYMENT_CIRCUIT_BREAKER).run(
                () -> restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(PaymentChargeResponse.class),
                throwable -> {
                    log.warn("Payment service unavailable", throwable);
                    throw new ServiceUnavailableException("Payment service is unavailable");
                });

        if (response == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        return response;
    }

    private PaymentDto getCustomerFromPaymentService(String uri) {
        PaymentDto paymentDto = circuitBreakerFactory.create(PAYMENT_CIRCUIT_BREAKER).run(
                () -> restClient
                        .get()
                        .uri(uri)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(PaymentDto.class),
                throwable -> {
                    log.warn("Payment service unavailable", throwable);
                    throw new ServiceUnavailableException("Payment service is unavailable");
                });

        if (paymentDto == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        return paymentDto;
    }

}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -f order/pom.xml test -Dtest=ChargeServiceImplTest`
Expected: PASS (5 tests)

- [ ] **Step 7: Thread `orderId` through the call site**

In `order/src/main/java/com/ganchevdimitarg/order/service/OrderServiceImpl.java`, change line 96 from:

```java
            payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount);
```

to:

```java
            payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount, String.valueOf(orderNumber));
```

- [ ] **Step 8: Run order's full test suite**

Run: `./mvnw -f order/pom.xml test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add order/src/main/java/com/ganchevdimitarg/order/dto/ChargeRequest.java order/src/main/java/com/ganchevdimitarg/order/dto/RefundRequest.java order/src/main/java/com/ganchevdimitarg/order/dto/PaymentChargeResponse.java order/src/main/java/com/ganchevdimitarg/order/service/ChargeService.java order/src/main/java/com/ganchevdimitarg/order/service/ChargeServiceImpl.java order/src/main/java/com/ganchevdimitarg/order/service/OrderServiceImpl.java order/src/test/java/com/ganchevdimitarg/order/service/ChargeServiceImplTest.java
git commit -m "fix(order): align charge/refund request bodies with payment's current API"
```

---

## Task 8: `gateway` — document the no-CORS decision

**Files:**
- Modify: `gateway/src/main/java/com/ganchevdimitarg/gateway/config/SecurityConfig.java`

No test — this is a documentation-only decision (no behavior change), per the design spec's resolution that no browser-based frontend calls gateway cross-origin today.

- [ ] **Step 1: Add a one-line rationale comment**

In `gateway/src/main/java/com/ganchevdimitarg/gateway/config/SecurityConfig.java`, add a comment directly above the `@Bean` method (line 14, `public SecurityWebFilterChain securityWebFilterChain(...)`):

```java
    // No CORS policy configured: no browser-based frontend calls this gateway
    // cross-origin today (only same-origin swagger-ui). Add an explicit
    // CorsConfigurationSource here if/when one is introduced — do not default to "*".
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
```

- [ ] **Step 2: Run gateway's test suite to confirm no regression**

Run: `./mvnw -f gateway/pom.xml test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add gateway/src/main/java/com/ganchevdimitarg/gateway/config/SecurityConfig.java
git commit -m "docs(gateway): record the deliberate no-CORS-policy decision"
```

---

## Final Verification

- [ ] Run the full standalone build for every touched module in dependency order:
  ```bash
  ./mvnw -f client/pom.xml clean install -DskipTests
  ./mvnw -f gateway/pom.xml clean verify
  ./mvnw -f catalog/pom.xml clean verify
  ./mvnw -f order/pom.xml clean verify
  ./mvnw -f payment/pom.xml clean verify
  ./mvnw -f notification/pom.xml clean verify
  ./mvnw -f profile/pom.xml clean verify
  ```
  Expected: BUILD SUCCESS for all seven.
- [ ] Manually confirm the original vulnerability is closed: start `gateway`, `payment`, and dependencies locally (or via each module's `compose.yaml`), send a POST to `payment`'s `/api/v1/payment/charge/create-charge` directly (bypassing gateway) with a valid `payment.write`-scoped token and a forged `X-User-Id` header but no `X-Gateway-Signature` — expect `401`.
- [ ] Manually confirm the legitimate path still works end-to-end: place a real order through `gateway` → `order` → `payment` (the `createOrder` flow) and confirm the charge succeeds — this exercises `GatewayHeaderPropagationInterceptor` (Task 6) forwarding the real gateway-signed headers and `payment`'s `GatewayTrustFilter` (Task 2/4) accepting them, together, not just each in isolation as their unit tests cover individually.
- [ ] Re-read `docs/reviews/2026-07-12-production-readiness-review.md` Categories 13 and 14 and confirm every cited gap in this workstream now has a corresponding fix; do not re-score the document itself (that's a separate step the user can request).
