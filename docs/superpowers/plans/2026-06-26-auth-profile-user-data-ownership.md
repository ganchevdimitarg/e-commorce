# Split auth & profile user data by ownership — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop auth and profile sharing one Mongo `users` collection — auth owns credentials in Postgres, profile owns personal data in Mongo, linked only by `userId`, with registration/deletion driven by Kafka choreography.

**Architecture:** Auth becomes the registration/deletion front door. It persists credentials in a new Postgres `user_credentials` table and emits `UserRegisteredEvent` / `UserDeletedEvent`. Profile consumes those events to create/soft-delete a `profiles` document keyed by `userId`. No field is duplicated except `userId`; profile reads `email`/`roles` from the JWT.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Authorization Server, Spring Data JPA + Flyway (auth/Postgres), Reactive Spring Data MongoDB (profile), Spring Kafka (JSON), Testcontainers.

**Spec:** `docs/superpowers/specs/2026-06-26-auth-profile-user-data-ownership-design.md`

## Global Constraints

- Java 25 · Spring Boot 4.1.0 · auth = WebMVC, profile = reactive WebFlux (kept as-is — do not migrate the stack).
- Package roots: `com.ganchevdimitarg.auth.*`, `com.ganchevdimitarg.profile.*`.
- JPA entities: `@Getter @Setter @NoArgsConstructor` only — never `@Data`. Records for DTOs/commands/events.
- Repositories return `Optional<T>` (auth) / `Mono<T>` (profile); unwrap via `orElseThrow`/`switchIfEmpty`.
- Domain exceptions extend `BusinessException(HttpStatus, String code, String message)`; all errors via `@ControllerAdvice` → problem+json. Never raw 500.
- Flyway owns schema: `V<n>__snake_case.sql`, monotonically increasing (next free = **V4**), `IF NOT EXISTS` guards, never edit a committed migration. `ddl-auto=validate` only.
- Every table: `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at … DEFAULT now()`, `deleted_at TIMESTAMPTZ NULL`. Soft-delete via `deleted_at = now()` — never hard `DELETE`. Queries filter `deleted_at IS NULL`.
- Kafka: topic `<domain>.<entity>.<event>`, consumer group `<service>-group`, DLT `<topic>.DLT`. JSON serialization (Jackson), no type headers — matches the existing `KafkaProducerConfig` (`JacksonJsonSerializer.noTypeInfo()`).
- Tests: JUnit 5 + AssertJ, Testcontainers (never H2/EmbeddedMongo), naming `should_<behavior>_when_<condition>`, no `Thread.sleep` (use Awaitility). Coverage 80% line / 100% domain.
- Build standalone (root reactor is blocked): `./mvnw -f authentication/pom.xml clean verify` and `./mvnw -f profile/pom.xml clean verify`.
- Commits: Conventional Commits, British English, end body with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

## Event contract (shared shape, copied into each service — no common-events module yet)

```java
// JSON payload, camelCase, tolerant consumer
UserRegisteredEvent(String userId, String email, java.util.Set<String> roles,
                    String firstName, String lastName,
                    String phoneNumber, String city, String street, String postCode,
                    java.time.Instant occurredAt)

UserDeletedEvent(String userId, java.time.Instant occurredAt)
```

Topics: `auth.user.registered` (DLT `auth.user.registered.DLT`), `auth.user.deleted` (DLT `auth.user.deleted.DLT`). Consumer group: `profile-group`.

## File structure

**Auth (`authentication/`)**
- `db/migration/V4__create_user_credentials.sql` — new credential table.
- `domain/UserCredential.java` — JPA entity (replaces Mongo `AuthUser`).
- `dao/UserCredentialRepository.java` — JPA repo (replaces `AuthUserDao`).
- `service/UserService.java` — read credentials from Postgres.
- `service/CredentialUserDetails.java` — `UserDetails` carrying `userId` + `email`.
- `event/UserRegisteredEvent.java`, `event/UserDeletedEvent.java` — outbound events.
- `config/kafka/KafkaProducerConfig.java`, `service/UserEventPublisher.java` — Kafka producer.
- `controller/AccountController.java` — `POST /register`, `DELETE /account`, `POST /password-reset`, `PATCH /set-new-password`.
- `service/AccountService.java` — credential create/soft-delete/password flows.
- `dto/RegisterUserCommand.java`, `dto/RegisterUserResponse.java`, `dto/SetNewPasswordCommand.java`.
- `domain/UserRole.java` — role enum.
- **Removed:** `domain/AuthUser.java`, `dao/AuthUserDao.java`, `domain/AuthUserTest.java`, Mongo bits of `UserServiceMongoIT`.

**Profile (`profile/`)**
- `domain/Profile.java` — re-keyed by `userId`, drop password/authorities.
- `dao/ProfileDao.java` — `findByUserIdAndDeletedAtIsNull`.
- `event/UserRegisteredEvent.java`, `event/UserDeletedEvent.java` — inbound events.
- `config/kafka/KafkaConsumerConfig.java` — consumer factory + DLT error handler.
- `listener/UserEventListener.java` — `@KafkaListener`s for both events.
- `service/ProfileService.java` / `ProfileServiceImpl.java` — `createProfileShell`, `softDeleteProfile`, `getByUserId`, `updateProfile`; payment-setup; remove register/delete/password methods.
- `controller/ProfileController.java` — remove `register-*` & `delete-user`; add `payment-setup`; re-key reads to `userId`.
- **Removed:** profile-side password reset/set-new-password, `register-admin/worker/user`.

---

## Task 1: Auth — `user_credentials` schema, entity, repository

**Files:**
- Create: `authentication/src/main/resources/db/migration/V4__create_user_credentials.sql`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/domain/UserCredential.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/domain/UserRole.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/dao/UserCredentialRepository.java`
- Test: `authentication/src/test/java/com/ganchevdimitarg/auth/dao/UserCredentialRepositoryIT.java`

**Interfaces:**
- Produces: `UserCredential` entity (fields `id:UUID`, `email:String`, `passwordHash:String`, `roles:Set<String>`, `enabled:boolean`, `deletedAt:Instant`); `UserCredentialRepository.findByEmailAndDeletedAtIsNull(String): Optional<UserCredential>`, `findByIdAndDeletedAtIsNull(UUID): Optional<UserCredential>`; `UserRole{ADMIN, WORKER, USER}` with `authorities():Set<String>`.

- [ ] **Step 1: Write the failing test**

```java
package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserCredentialRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserCredentialRepository repository;

    @Test
    void should_findActiveByEmail_when_notSoftDeleted() {
        UserCredential saved = repository.save(newCredential("alice@test.io"));

        assertThat(repository.findByEmailAndDeletedAtIsNull("alice@test.io"))
                .get().extracting(UserCredential::getId).isEqualTo(saved.getId());
    }

    @Test
    void should_notFindByEmail_when_softDeleted() {
        UserCredential c = newCredential("bob@test.io");
        c.setDeletedAt(java.time.Instant.now());
        repository.save(c);

        assertThat(repository.findByEmailAndDeletedAtIsNull("bob@test.io")).isEmpty();
    }

    private UserCredential newCredential(String email) {
        UserCredential c = new UserCredential();
        c.setId(UUID.randomUUID());
        c.setEmail(email);
        c.setPasswordHash("{bcrypt}x");
        c.setRoles(Set.of("ROLE_USER"));
        c.setEnabled(true);
        return c;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f authentication/pom.xml -Dtest=UserCredentialRepositoryIT test`
Expected: FAIL — `UserCredential` / `UserCredentialRepository` do not exist (compile error).

- [ ] **Step 3: Write the migration**

`V4__create_user_credentials.sql`:
```sql
CREATE TABLE IF NOT EXISTS user_credentials
(
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    roles         varchar(255) NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted_at    timestamptz  NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_credentials_email
    ON user_credentials (email)
    WHERE deleted_at IS NULL;
```

- [ ] **Step 4: Write `UserRole`**

```java
package com.ganchevdimitarg.auth.domain;

import java.util.Set;

public enum UserRole {
    ADMIN(Set.of("ROLE_ADMIN")),
    WORKER(Set.of("ROLE_WORKER")),
    USER(Set.of("ROLE_USER"));

    private final Set<String> authorities;

    UserRole(Set<String> authorities) { this.authorities = authorities; }

    public Set<String> authorities() { return authorities; }
}
```

- [ ] **Step 5: Write `UserCredential` entity**

```java
package com.ganchevdimitarg.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_credentials")
@Getter
@Setter
@NoArgsConstructor
public class UserCredential {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_credential_roles",
            joinColumns = @JoinColumn(name = "credential_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

> **Note:** the `@ElementCollection` needs its own table. Add a second statement to `V4` (same migration, one logical change = the credential aggregate):
> ```sql
> CREATE TABLE IF NOT EXISTS user_credential_roles
> (
>     credential_id uuid        NOT NULL REFERENCES user_credentials (id),
>     role          varchar(64) NOT NULL,
>     PRIMARY KEY (credential_id, role)
> );
> ```

- [ ] **Step 6: Write the repository**

```java
package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmailAndDeletedAtIsNull(String email);

    Optional<UserCredential> findByIdAndDeletedAtIsNull(UUID id);
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw -f authentication/pom.xml -Dtest=UserCredentialRepositoryIT test`
Expected: PASS (Flyway applies V4, both tests green).

- [ ] **Step 8: Commit**

```bash
git add authentication/src/main/resources/db/migration/V4__create_user_credentials.sql \
        authentication/src/main/java/com/ganchevdimitarg/auth/domain/UserCredential.java \
        authentication/src/main/java/com/ganchevdimitarg/auth/domain/UserRole.java \
        authentication/src/main/java/com/ganchevdimitarg/auth/dao/UserCredentialRepository.java \
        authentication/src/test/java/com/ganchevdimitarg/auth/dao/UserCredentialRepositoryIT.java
git commit -m "feat(auth): add user_credentials table, entity and repository

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Auth — `UserService` reads Postgres; retire Mongo `AuthUser`

**Files:**
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/service/CredentialUserDetails.java`
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/service/UserService.java`
- Delete: `authentication/src/main/java/com/ganchevdimitarg/auth/domain/AuthUser.java`, `authentication/src/main/java/com/ganchevdimitarg/auth/dao/AuthUserDao.java`, `authentication/src/test/java/com/ganchevdimitarg/auth/domain/AuthUserTest.java`
- Replace: `authentication/src/test/java/com/ganchevdimitarg/auth/service/UserServiceMongoIT.java` → `UserServicePersistenceIT.java`
- Modify/Review: `authentication/src/test/java/com/ganchevdimitarg/auth/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserCredentialRepository` (Task 1).
- Produces: `CredentialUserDetails` (implements `UserDetails`; `getUsername()` returns the **userId** string, plus `email():String`); `UserService.loadUserByUsername(email)` returns a `CredentialUserDetails`.

- [ ] **Step 1: Write the failing test** (`UserServicePersistenceIT.java`)

```java
package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServicePersistenceIT extends AbstractIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserCredentialRepository repository;

    @Test
    void should_loadByEmail_andExposeUserIdAsUsername_when_present() {
        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id);
        c.setEmail("user@test.io");
        c.setPasswordHash("{noop}pw");
        c.setRoles(Set.of("ROLE_USER"));
        c.setEnabled(true);
        repository.save(c);

        CredentialUserDetails details =
                (CredentialUserDetails) userService.loadUserByUsername("user@test.io");

        assertThat(details.getUsername()).isEqualTo(id.toString());
        assertThat(details.email()).isEqualTo("user@test.io");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void should_throw_when_userAbsent() {
        assertThatThrownBy(() -> userService.loadUserByUsername("nobody@test.io"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f authentication/pom.xml -Dtest=UserServicePersistenceIT test`
Expected: FAIL — `CredentialUserDetails` missing; `UserService` still Mongo-backed.

- [ ] **Step 3: Write `CredentialUserDetails`**

```java
package com.ganchevdimitarg.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public final class CredentialUserDetails implements UserDetails {

    private final String userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> roles;

    public CredentialUserDetails(String userId, String email, String passwordHash,
                                 boolean enabled, Set<String> roles) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
    }

    public String email() { return email; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return userId; }       // sub = userId
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
```

- [ ] **Step 4: Rewrite `UserService`**

```java
package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserCredentialRepository repository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserCredential c = repository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return new CredentialUserDetails(
                c.getId().toString(), c.getEmail(), c.getPasswordHash(),
                c.isEnabled(), c.getRoles());
    }
}
```

- [ ] **Step 5: Delete the Mongo user code and stale tests**

```bash
git rm authentication/src/main/java/com/ganchevdimitarg/auth/domain/AuthUser.java \
       authentication/src/main/java/com/ganchevdimitarg/auth/dao/AuthUserDao.java \
       authentication/src/test/java/com/ganchevdimitarg/auth/domain/AuthUserTest.java \
       authentication/src/test/java/com/ganchevdimitarg/auth/service/UserServiceMongoIT.java
```
Then open `UserServiceTest.java` and replace any `AuthUser`/`AuthUserDao` mocks with `UserCredentialRepository` returning a `UserCredential` (mirror the IT setup). If the unit test adds no value beyond the IT, delete it.

- [ ] **Step 6: Run the auth suite**

Run: `./mvnw -f authentication/pom.xml clean test`
Expected: PASS — no references to `AuthUser` remain (`grep -r AuthUser authentication/src` returns nothing).

- [ ] **Step 7: Commit**

```bash
git add -A authentication/src
git commit -m "refactor(auth): read credentials from Postgres, drop Mongo AuthUser

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Auth — Kafka producer infrastructure + event records

**Files:**
- Modify: `authentication/pom.xml` (add `spring-kafka`, test `spring-kafka-test`, `testcontainers-kafka`)
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/event/UserRegisteredEvent.java`, `event/UserDeletedEvent.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/config/kafka/KafkaProducerConfig.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/service/UserEventPublisher.java`
- Modify: `authentication/src/main/resources/application-dev.yml`, `authentication/src/test/resources/application-test.yml`
- Modify: `authentication/src/test/java/com/ganchevdimitarg/auth/AbstractIntegrationTest.java` (add Kafka container)
- Test: `authentication/src/test/java/com/ganchevdimitarg/auth/service/UserEventPublisherIT.java`

**Interfaces:**
- Produces: `UserRegisteredEvent` / `UserDeletedEvent` records (see Event contract above, package `com.ganchevdimitarg.auth.event`); `UserEventPublisher.publishRegistered(UserRegisteredEvent)`, `publishDeleted(UserDeletedEvent)`.

- [ ] **Step 1: Add dependencies to `authentication/pom.xml`**

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the event records**

```java
package com.ganchevdimitarg.auth.event;

import java.time.Instant;
import java.util.Set;

public record UserRegisteredEvent(
        String userId, String email, Set<String> roles,
        String firstName, String lastName,
        String phoneNumber, String city, String street, String postCode,
        Instant occurredAt) {}
```

```java
package com.ganchevdimitarg.auth.event;

import java.time.Instant;

public record UserDeletedEvent(String userId, Instant occurredAt) {}
```

- [ ] **Step 3: Write the failing test** (`UserEventPublisherIT.java`)

```java
package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventPublisherIT extends AbstractIntegrationTest {

    @Autowired private UserEventPublisher publisher;

    @Test
    void should_publishRegisteredEvent_toTopic() {
        var consumer = newConsumer("auth.user.registered");
        String userId = UUID.randomUUID().toString();

        publisher.publishRegistered(new UserRegisteredEvent(
                userId, "e@test.io", Set.of("ROLE_USER"),
                "Anna", "Smith", "0888123456", "Sofia", "Main", "1000", Instant.now()));

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "auth.user.registered");
        assertThat(record.value()).contains(userId).contains("e@test.io");
    }
    // newConsumer(...) helper: see AbstractIntegrationTest Step 6
}
```

- [ ] **Step 4: Write `KafkaProducerConfig`** (mirror profile's no-type-info pattern, but generic value)

```java
package com.ganchevdimitarg.auth.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            return new DefaultKafkaProducerFactory<>(
                    Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                    new StringSerializer(),
                    serializer.noTypeInfo());
        }
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
```

- [ ] **Step 5: Write `UserEventPublisher`**

```java
package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.event.UserDeletedEvent;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    static final String REGISTERED_TOPIC = "auth.user.registered";
    static final String DELETED_TOPIC = "auth.user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(REGISTERED_TOPIC, event.userId(), event);
        log.info("Published UserRegisteredEvent for userId {}", event.userId());
    }

    public void publishDeleted(UserDeletedEvent event) {
        kafkaTemplate.send(DELETED_TOPIC, event.userId(), event);
        log.info("Published UserDeletedEvent for userId {}", event.userId());
    }
}
```

- [ ] **Step 6: Add a Kafka container to `AbstractIntegrationTest`**

Add the field + a `newConsumer` helper:
```java
@ServiceConnection
static final org.testcontainers.kafka.KafkaContainer KAFKA =
        new org.testcontainers.kafka.KafkaContainer("apache/kafka:3.8.1");

static { KAFKA.start(); }   // alongside POSTGRES.start(); MONGO.start();

protected static org.apache.kafka.clients.consumer.Consumer<String, String> newConsumer(String topic) {
    var props = org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps(
            KAFKA.getBootstrapServers(), "it-" + topic, "true");
    props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class);
    props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class);
    var consumer = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, String>(props)
            .createConsumer();
    consumer.subscribe(java.util.List.of(topic));
    return consumer;
}
```

- [ ] **Step 7: Wire bootstrap-servers config**

In `application-dev.yml` add under `spring:` → `kafka:\n    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`. `application-test.yml` gets `spring.kafka.bootstrap-servers` from `@ServiceConnection` automatically — no entry needed, but confirm the file has no hard-coded override.

- [ ] **Step 8: Run the test**

Run: `./mvnw -f authentication/pom.xml -Dtest=UserEventPublisherIT test`
Expected: PASS — the registered event lands on `auth.user.registered`.

- [ ] **Step 9: Commit**

```bash
git add authentication/pom.xml authentication/src/main/java/com/ganchevdimitarg/auth/event \
        authentication/src/main/java/com/ganchevdimitarg/auth/config/kafka \
        authentication/src/main/java/com/ganchevdimitarg/auth/service/UserEventPublisher.java \
        authentication/src/main/resources/application-dev.yml \
        authentication/src/test/java/com/ganchevdimitarg/auth/AbstractIntegrationTest.java \
        authentication/src/test/java/com/ganchevdimitarg/auth/service/UserEventPublisherIT.java
git commit -m "feat(auth): add Kafka producer and user lifecycle events

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Auth — registration endpoint (`POST /api/v1/auth/register`)

**Files:**
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/dto/RegisterUserCommand.java`, `dto/RegisterUserResponse.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/service/AccountService.java`
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/controller/AccountController.java`
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/config/security/DefaultSecurityConfig.java` (permit `POST /api/v1/auth/register`)
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/config/security/AuthorizationServerConfig.java` (add `email` claim)
- Test: `authentication/src/test/java/com/ganchevdimitarg/auth/controller/AccountControllerRegisterIT.java`

**Interfaces:**
- Consumes: `UserCredentialRepository` (T1), `PasswordEncoder` (existing bean), `UserEventPublisher` (T3), `UserRole` (T1).
- Produces: `AccountService.register(RegisterUserCommand): RegisterUserResponse`; `RegisterUserResponse(String userId)`.

- [ ] **Step 1: Write the failing test**

```java
package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AccountControllerRegisterIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserCredentialRepository repository;

    @Test
    void should_create_credentialAndEmitEvent_when_registerValid() throws Exception {
        var consumer = newConsumer("auth.user.registered");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"new@test.io","password":"Aa1@aaaa","role":"USER",
                           "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
                           "city":"Sofia","street":"Main","postCode":"1000"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        assertThat(repository.findByEmailAndDeletedAtIsNull("new@test.io")).isPresent();
        assertThat(org.springframework.kafka.test.utils.KafkaTestUtils
                .getSingleRecord(consumer, "auth.user.registered").value())
                .contains("new@test.io");
    }

    @Test
    void should_returnConflict_when_emailAlreadyRegistered() throws Exception {
        String body = """
              {"email":"dupe@test.io","password":"Aa1@aaaa","role":"USER",
               "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
               "city":"Sofia","street":"Main","postCode":"1000"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f authentication/pom.xml -Dtest=AccountControllerRegisterIT test`
Expected: FAIL — endpoint 404 / classes missing.

- [ ] **Step 3: Write the command + response records**

```java
package com.ganchevdimitarg.auth.dto;

import com.ganchevdimitarg.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserCommand(
        @NotBlank @Email @Size(min = 5, max = 50) String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,30}$",
                 message = "password does not meet complexity rules") String password,
        @NotNull UserRole role,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phoneNumber,
        @NotBlank String city,
        @NotBlank String street,
        @NotBlank String postCode) {}
```

```java
package com.ganchevdimitarg.auth.dto;

public record RegisterUserResponse(String userId) {}
```

- [ ] **Step 4: Write `AccountService.register`**

```java
package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import com.ganchevdimitarg.auth.dto.RegisterUserCommand;
import com.ganchevdimitarg.auth.dto.RegisterUserResponse;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import com.ganchevdimitarg.auth.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher publisher;

    @Transactional
    public RegisterUserResponse register(RegisterUserCommand cmd) {
        repository.findByEmailAndDeletedAtIsNull(cmd.email()).ifPresent(existing -> {
            throw new ConflictException("Email already registered: " + cmd.email());
        });

        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id);
        c.setEmail(cmd.email());
        c.setPasswordHash(passwordEncoder.encode(cmd.password()));
        c.setRoles(cmd.role().authorities());
        c.setEnabled(true);
        repository.save(c);

        publisher.publishRegistered(new UserRegisteredEvent(
                id.toString(), cmd.email(), cmd.role().authorities(),
                cmd.firstName(), cmd.lastName(), cmd.phoneNumber(),
                cmd.city(), cmd.street(), cmd.postCode(), Instant.now()));

        log.info("Registered user {} with role {}", cmd.email(), cmd.role());
        return new RegisterUserResponse(id.toString());
    }
}
```

> Verify `ConflictException` constructor signature matches `authentication/src/main/java/com/ganchevdimitarg/auth/exception/ConflictException.java` before use.

- [ ] **Step 5: Write `AccountController`**

```java
package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.dto.RegisterUserCommand;
import com.ganchevdimitarg.auth.dto.RegisterUserResponse;
import com.ganchevdimitarg.auth.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(cmd));
    }
}
```

- [ ] **Step 6: Permit the registration endpoint**

In `DefaultSecurityConfig`, add before `.anyRequest().authenticated()`:
```java
.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/register").permitAll()
```
And CSRF: since the chain enables CSRF (`.csrf(Customizer.withDefaults())`), exempt the API path:
```java
.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/auth/**"))
```
(Replace the existing `.csrf(Customizer.withDefaults())` line.)

- [ ] **Step 7: Add the `email` claim** in `AuthorizationServerConfig.jwtCustomizer()`

Inside the `if (ACCESS_TOKEN)` block, after the `scope` claim:
```java
if (principal.getPrincipal() instanceof com.ganchevdimitarg.auth.service.CredentialUserDetails cud) {
    context.getClaims().claim("email", cud.email());
}
```

- [ ] **Step 8: Run the test**

Run: `./mvnw -f authentication/pom.xml -Dtest=AccountControllerRegisterIT test`
Expected: PASS — 201 + userId, credential persisted, event emitted, duplicate → 409.

- [ ] **Step 9: Commit**

```bash
git add authentication/src/main/java/com/ganchevdimitarg/auth/dto \
        authentication/src/main/java/com/ganchevdimitarg/auth/service/AccountService.java \
        authentication/src/main/java/com/ganchevdimitarg/auth/controller/AccountController.java \
        authentication/src/main/java/com/ganchevdimitarg/auth/config/security/DefaultSecurityConfig.java \
        authentication/src/main/java/com/ganchevdimitarg/auth/config/security/AuthorizationServerConfig.java \
        authentication/src/test/java/com/ganchevdimitarg/auth/controller/AccountControllerRegisterIT.java
git commit -m "feat(auth): add user registration endpoint emitting UserRegisteredEvent

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Auth — account deletion + password flows

**Files:**
- Create: `authentication/src/main/java/com/ganchevdimitarg/auth/dto/SetNewPasswordCommand.java`
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/service/AccountService.java` (`deleteOwnAccount`, `requestPasswordReset`, `setNewPassword`)
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/controller/AccountController.java` (`DELETE /account`, `POST /password-reset`, `PATCH /set-new-password`)
- Modify: `authentication/src/main/java/com/ganchevdimitarg/auth/config/security/DefaultSecurityConfig.java` (permit `POST /password-reset`, `PATCH /set-new-password`; `DELETE /account` stays authenticated)
- Test: `authentication/src/test/java/com/ganchevdimitarg/auth/controller/AccountControllerDeleteIT.java`

**Interfaces:**
- Consumes: `UserCredentialRepository`, `UserEventPublisher`, `PasswordEncoder`.
- Produces: `AccountService.deleteOwnAccount(String userId)`, `setNewPassword(SetNewPasswordCommand)`; `SetNewPasswordCommand(String email, String password)`.

- [ ] **Step 1: Write the failing test**

```java
package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerDeleteIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserCredentialRepository repository;

    @Test
    void should_softDeleteAndEmitEvent_when_deleteOwnAccount() throws Exception {
        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id); c.setEmail("del@test.io"); c.setPasswordHash("{noop}pw");
        c.setRoles(Set.of("ROLE_USER")); c.setEnabled(true);
        repository.save(c);
        var consumer = newConsumer("auth.user.deleted");

        mvc.perform(delete("/api/v1/auth/account").with(authUser(id.toString())))
                .andExpect(status().isNoContent());

        assertThat(repository.findByIdAndDeletedAtIsNull(id)).isEmpty();        // soft-deleted
        assertThat(repository.findById(id)).get()                              // row still exists
                .satisfies(row -> { assertThat(row.getDeletedAt()).isNotNull();
                                    assertThat(row.isEnabled()).isFalse(); });
        assertThat(org.springframework.kafka.test.utils.KafkaTestUtils
                .getSingleRecord(consumer, "auth.user.deleted").value()).contains(id.toString());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authUser(String userId) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(userId).roles("USER");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f authentication/pom.xml -Dtest=AccountControllerDeleteIT test`
Expected: FAIL — `DELETE /account` 404.

- [ ] **Step 3: Add `deleteOwnAccount` + password methods to `AccountService`**

```java
@Transactional
public void deleteOwnAccount(String userId) {
    UserCredential c = repository.findByIdAndDeletedAtIsNull(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new com.ganchevdimitarg.auth.exception.NotFoundException("user", userId));
    c.setDeletedAt(java.time.Instant.now());
    c.setEnabled(false);
    repository.save(c);
    publisher.publishDeleted(new com.ganchevdimitarg.auth.event.UserDeletedEvent(userId, java.time.Instant.now()));
    log.info("Soft-deleted account {}", userId);
}

@Transactional
public void setNewPassword(com.ganchevdimitarg.auth.dto.SetNewPasswordCommand cmd) {
    UserCredential c = repository.findByEmailAndDeletedAtIsNull(cmd.email())
            .orElseThrow(() -> new com.ganchevdimitarg.auth.exception.NotFoundException("user", cmd.email()));
    c.setPasswordHash(passwordEncoder.encode(cmd.password()));
    repository.save(c);
    log.info("Password changed for {}", cmd.email());
}
```

> Confirm `NotFoundException(String resource, Object id)` signature in `exception/NotFoundException.java`.

`SetNewPasswordCommand`:
```java
package com.ganchevdimitarg.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SetNewPasswordCommand(@NotBlank String email, @NotBlank String password) {}
```

> **Password-reset token email:** the spec moves reset-token delivery to auth. Auth has no mail channel yet. Scope-control decision: implement `setNewPassword` (the credential mutation auth owns) now; the reset-*token* email remains delivered by the notification service. Add `POST /api/v1/auth/password-reset` that validates the email exists and publishes a `NotificationDto`-shaped message to the existing mail topic **only if** auth already has a producer for it — otherwise leave a single-line `[ticket]`-tagged note and keep reset-token mailing in profile until a follow-up. Do **not** invent a new mail pipeline in this task.

- [ ] **Step 4: Add controller methods**

```java
@org.springframework.web.bind.annotation.DeleteMapping("/account")
public ResponseEntity<Void> deleteAccount(org.springframework.security.core.Authentication auth) {
    accountService.deleteOwnAccount(auth.getName());          // name == userId
    return ResponseEntity.noContent().build();
}

@org.springframework.web.bind.annotation.PatchMapping("/set-new-password")
public ResponseEntity<Void> setNewPassword(
        @Valid @RequestBody com.ganchevdimitarg.auth.dto.SetNewPasswordCommand cmd) {
    accountService.setNewPassword(cmd);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 5: Permit the password endpoints** in `DefaultSecurityConfig` (keep `/account` authenticated):

```java
.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/auth/set-new-password").permitAll()
.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/password-reset").permitAll()
```

- [ ] **Step 6: Run the test**

Run: `./mvnw -f authentication/pom.xml -Dtest=AccountControllerDeleteIT test`
Expected: PASS — soft-delete (row kept, `deleted_at` set, disabled) + `UserDeletedEvent` emitted.

- [ ] **Step 7: Full auth verify + commit**

Run: `./mvnw -f authentication/pom.xml clean verify && ./mvnw -f authentication/pom.xml checkstyle:check && ./mvnw -f authentication/pom.xml flyway:validate`
Expected: BUILD SUCCESS, no checkstyle violations, migrations validate.

```bash
git add authentication/src
git commit -m "feat(auth): add account deletion and password mutation emitting UserDeletedEvent

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Profile — re-key `Profile` by `userId`, drop credential fields

**Files:**
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/domain/Profile.java`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/dao/ProfileDao.java`
- Test: `profile/src/test/java/com/ganchevdimitarg/profile/dao/ProfileDaoIT.java`

**Interfaces:**
- Produces: `Profile` with `@Id userId:String`, `firstName/lastName/phoneNumber/address/created/deletedAt`, **no** `password`/`grantedAuthorities`, collection `profiles`; `ProfileDao.findByUserIdAndDeletedAtIsNull(String): Mono<Profile>`.

- [ ] **Step 1: Write the failing test**

```java
package com.ganchevdimitarg.profile.dao;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

class ProfileDaoIT extends BaseTest {

    @Autowired private ProfileDao profileDao;

    @Test
    void should_findActiveByUserId_when_notDeleted() {
        String userId = UUID.randomUUID().toString();
        Profile p = Profile.builder()
                .userId(userId).firstName("Anna").lastName("Smith")
                .phoneNumber("888123456").address(new Address("Sofia", "Main", "1000"))
                .created(LocalDateTime.now()).build();

        StepVerifier.create(profileDao.save(p).then(profileDao.findByUserIdAndDeletedAtIsNull(userId)))
                .expectNextMatches(found -> found.getUserId().equals(userId)
                        && found.getFirstName().equals("Anna"))
                .verifyComplete();
    }
}
```

> Check `BaseTest` (`profile/src/test/java/com/ganchevdimitarg/profile/config/BaseTest.java`) provides a Mongo Testcontainer; if it only mocks, extend the Testcontainers base instead — never EmbeddedMongo.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f profile/pom.xml -Dtest=ProfileDaoIT test`
Expected: FAIL — `userId`/`findByUserIdAndDeletedAtIsNull` missing.

- [ ] **Step 3: Rewrite `Profile`**

```java
package com.ganchevdimitarg.profile.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

@Document(collection = "profiles")
@Getter
@Setter
@Builder
public class Profile {

    @Id
    private String userId;          // shared key from auth
    private String firstName;
    private String lastName;
    private Address address;
    private String phoneNumber;
    private LocalDateTime created;
    private Instant deletedAt;
}
```

> Drop `@Data` (entity with no business key); use `@Getter/@Setter/@Builder`. Remove `password`, `grantedAuthorities`, `username`, and all Bean-Validation annotations (validation now lives on the auth-side command).

- [ ] **Step 4: Update `ProfileDao`**

```java
package com.ganchevdimitarg.profile.dao;

import com.ganchevdimitarg.profile.domain.Profile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ProfileDao extends ReactiveMongoRepository<Profile, String> {

    Mono<Profile> findByUserIdAndDeletedAtIsNull(String userId);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -f profile/pom.xml -Dtest=ProfileDaoIT test`
Expected: PASS. (`ProfileServiceImpl` will not compile yet — that's Task 9; if the module must compile between tasks, do Tasks 6–9 as one branch and run the module build at Task 9.)

- [ ] **Step 6: Commit**

```bash
git add profile/src/main/java/com/ganchevdimitarg/profile/domain/Profile.java \
        profile/src/main/java/com/ganchevdimitarg/profile/dao/ProfileDao.java \
        profile/src/test/java/com/ganchevdimitarg/profile/dao/ProfileDaoIT.java
git commit -m "refactor(profile): key Profile by userId, drop credential fields

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Profile — Kafka consumer + `UserRegisteredEvent` listener (profile shell)

**Files:**
- Create: `profile/src/main/java/com/ganchevdimitarg/profile/event/UserRegisteredEvent.java`, `event/UserDeletedEvent.java`
- Create: `profile/src/main/java/com/ganchevdimitarg/profile/config/kafka/KafkaConsumerConfig.java`
- Create: `profile/src/main/java/com/ganchevdimitarg/profile/listener/UserEventListener.java`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java` / `ProfileServiceImpl.java` (add `createProfileShell`)
- Modify: `profile/src/main/resources/application-dev.yml`, `profile/src/test/resources/application-test.yml`
- Test: `profile/src/test/java/com/ganchevdimitarg/profile/listener/UserRegisteredListenerIT.java`

**Interfaces:**
- Consumes: events from `auth.user.registered`.
- Produces: `ProfileService.createProfileShell(UserRegisteredEvent): Mono<Void>` (idempotent on `userId`); `@KafkaListener` on group `profile-group`.

- [ ] **Step 1: Write the failing test** (Testcontainers Mongo + Kafka)

```java
package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.config.RedisKafkaBaseTest;   // or the module's Mongo+Kafka base
import com.ganchevdimitarg.profile.dao.ProfileDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class UserRegisteredListenerIT extends RedisKafkaBaseTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProfileDao profileDao;

    @Test
    void should_createProfileShell_when_userRegisteredConsumed() {
        String userId = UUID.randomUUID().toString();
        kafkaTemplate.send("auth.user.registered", userId, """
            {"userId":"%s","email":"e@test.io","roles":["ROLE_USER"],
             "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
             "city":"Sofia","street":"Main","postCode":"1000","occurredAt":"2026-06-26T00:00:00Z"}"""
            .formatted(userId));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(profileDao.findByUserIdAndDeletedAtIsNull(userId).block()).isNotNull());
    }
}
```

> If the module has no Mongo+Kafka Testcontainers base yet, create `RedisKafkaBaseTest`/`MongoKafkaBaseTest` extending the existing Mongo base and adding a `@Container KafkaContainer` with `@DynamicPropertySource` setting `spring.kafka.bootstrap-servers`. Never EmbeddedKafka with a real broker assertion — use Testcontainers `apache/kafka:3.8.1`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f profile/pom.xml -Dtest=UserRegisteredListenerIT test`
Expected: FAIL — no listener; profile shell never created.

- [ ] **Step 3: Write the inbound event records** (tolerant — extra/missing fields OK)

```java
package com.ganchevdimitarg.profile.event;

import java.util.Set;

public record UserRegisteredEvent(
        String userId, String email, Set<String> roles,
        String firstName, String lastName,
        String phoneNumber, String city, String street, String postCode,
        String occurredAt) {}
```
```java
package com.ganchevdimitarg.profile.event;

public record UserDeletedEvent(String userId, String occurredAt) {}
```

- [ ] **Step 4: Write `KafkaConsumerConfig`** (JSON, no type headers, DLT on failure)

```java
package com.ganchevdimitarg.profile.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;

    public KafkaConsumerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "profile-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory());
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2)));
        return factory;
    }
}
```

> The existing `profile/.../config/KafkaProducerConfig` produces `KafkaTemplate<String, NotificationDto>`. Add a second, generic `KafkaTemplate<String, Object>` bean (qualified) for the DLT recoverer, or widen the producer config — pick one and keep the `NotificationDto` mail path working. Verify `MailServiceImpl` still resolves its template (qualify beans if needed).

- [ ] **Step 5: Add `createProfileShell` to `ProfileServiceImpl`** (idempotent)

```java
@Override
public Mono<Void> createProfileShell(com.ganchevdimitarg.profile.event.UserRegisteredEvent e) {
    return profileDao.findByUserIdAndDeletedAtIsNull(e.userId())
            .flatMap(existing -> Mono.<Profile>empty())                       // idempotent: already exists
            .switchIfEmpty(profileDao.insert(Profile.builder()
                    .userId(e.userId())
                    .firstName(e.firstName())
                    .lastName(e.lastName())
                    .phoneNumber(e.phoneNumber())
                    .address(new Address(e.city(), e.street(), e.postCode()))
                    .created(java.time.LocalDateTime.now())
                    .build()))
            .doOnSuccess(p -> log.info("Profile shell created for userId {}", e.userId()))
            .then();
}
```
Add `Mono<Void> createProfileShell(UserRegisteredEvent e);` to the `ProfileService` interface.

- [ ] **Step 6: Write `UserEventListener`**

```java
package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.event.UserRegisteredEvent;
import com.ganchevdimitarg.profile.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user.registered", groupId = "profile-group")
    public void onUserRegistered(String payload) throws Exception {
        UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
        profileService.createProfileShell(event).block();      // listener thread; block is acceptable
    }
}
```

> Ensure an `ObjectMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES=false` is available (project `JacksonConfig`). A parse failure propagates → `DefaultErrorHandler` routes to `auth.user.registered.DLT`.

- [ ] **Step 7: Add bootstrap-servers config** to `application-dev.yml` (`spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`); test profile inherits from the Testcontainers base.

- [ ] **Step 8: Run the test**

Run: `./mvnw -f profile/pom.xml -Dtest=UserRegisteredListenerIT test`
Expected: PASS — profile shell created within 10s.

- [ ] **Step 9: Commit**

```bash
git add profile/src/main/java/com/ganchevdimitarg/profile/event \
        profile/src/main/java/com/ganchevdimitarg/profile/config/kafka \
        profile/src/main/java/com/ganchevdimitarg/profile/listener/UserEventListener.java \
        profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java \
        profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileServiceImpl.java \
        profile/src/main/resources/application-dev.yml \
        profile/src/test/java/com/ganchevdimitarg/profile/listener/UserRegisteredListenerIT.java
git commit -m "feat(profile): consume UserRegisteredEvent to create profile shell

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Profile — `UserDeletedEvent` listener (soft-delete + payment cleanup)

**Files:**
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/listener/UserEventListener.java` (add deleted listener)
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java` / `ProfileServiceImpl.java` (add `softDeleteProfile`; reuse payment cleanup)
- Test: `profile/src/test/java/com/ganchevdimitarg/profile/listener/UserDeletedListenerIT.java`

**Interfaces:**
- Consumes: events from `auth.user.deleted`.
- Produces: `ProfileService.softDeleteProfile(String userId): Mono<Void>` (sets `deletedAt`, runs `deletePaymentCustomer`).

- [ ] **Step 1: Write the failing test**

```java
package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.config.RedisKafkaBaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UserDeletedListenerIT extends RedisKafkaBaseTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProfileDao profileDao;

    @Test
    void should_softDeleteProfile_when_userDeletedConsumed() {
        String userId = UUID.randomUUID().toString();
        profileDao.save(Profile.builder().userId(userId).firstName("Anna").lastName("Smith")
                .phoneNumber("888123456").address(new Address("Sofia","Main","1000"))
                .created(LocalDateTime.now()).build()).block();

        kafkaTemplate.send("auth.user.deleted", userId,
                "{\"userId\":\"%s\",\"occurredAt\":\"2026-06-26T00:00:00Z\"}".formatted(userId));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(profileDao.findByUserIdAndDeletedAtIsNull(userId).block()).isNull());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -f profile/pom.xml -Dtest=UserDeletedListenerIT test`
Expected: FAIL — no deleted listener.

- [ ] **Step 3: Add `softDeleteProfile` to `ProfileServiceImpl`** (reuse existing `deletePaymentCustomer`, re-keyed by userId — pass the profile's stored data or look up payment by userId)

```java
@Override
public Mono<Void> softDeleteProfile(String userId) {
    return profileDao.findByUserIdAndDeletedAtIsNull(userId)
            .switchIfEmpty(Mono.error(new InvalidRequestDataException(PROFILE_DOES_NOT_EXIST)))
            .flatMap(profile -> deletePaymentCustomer(userId)            // existing private method, keyed by userId
                    .then(Mono.defer(() -> {
                        profile.setDeletedAt(java.time.Instant.now());
                        return profileDao.save(profile);
                    })))
            .doOnSuccess(p -> log.info("Profile soft-deleted for userId {}", userId))
            .then();
}
```
Add `Mono<Void> softDeleteProfile(String userId);` to the interface. Update `deletePaymentCustomer(String)` callers/URI to key by `userId` (payment service contract is out of scope; keep the existing URI shape, substituting userId for username).

- [ ] **Step 4: Add the deleted listener**

```java
@KafkaListener(topics = "auth.user.deleted", groupId = "profile-group")
public void onUserDeleted(String payload) throws Exception {
    com.ganchevdimitarg.profile.event.UserDeletedEvent event =
            objectMapper.readValue(payload, com.ganchevdimitarg.profile.event.UserDeletedEvent.class);
    profileService.softDeleteProfile(event.userId()).block();
}
```

- [ ] **Step 5: Run the test**

Run: `./mvnw -f profile/pom.xml -Dtest=UserDeletedListenerIT test`
Expected: PASS — profile no longer found by active query (soft-deleted).

- [ ] **Step 6: Commit**

```bash
git add profile/src/main/java/com/ganchevdimitarg/profile/listener/UserEventListener.java \
        profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java \
        profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileServiceImpl.java \
        profile/src/test/java/com/ganchevdimitarg/profile/listener/UserDeletedListenerIT.java
git commit -m "feat(profile): consume UserDeletedEvent to soft-delete profile

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Profile — re-key endpoints to `userId`, remove register/delete/password

**Files:**
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/controller/ProfileController.java`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/service/ProfileService.java` / `ProfileServiceImpl.java`
- Modify: `profile/src/main/java/com/ganchevdimitarg/profile/dto/UserDto.java` (drop password/authorities if present; keep display fields)
- Modify/Delete affected tests: `ProfileControllerTest`, `ProfileControllerIntegrationTest`, `ProfileServiceTest`
- Test: add `getByUserId` / `payment-setup` cases

**Interfaces:**
- Produces: `GET /api/v1/profile/me` (userId from JWT), `PUT /api/v1/profile/me`, `POST /api/v1/profile/payment-setup`; `ProfileService.getByUserId(String): Mono<UserDto>`, `updateProfile(String userId, UpdateProfileCommand): Mono<Void>`, `setupPayment(String userId, CardSetupCommand): Mono<UserDto>`.

- [ ] **Step 1: Write the failing test** (controller slice, userId-keyed)

```java
// In ProfileControllerTest (WebFluxTest/MockMvc per existing style):
// should_returnProfile_when_getMe → GET /api/v1/profile/me with authentication.name = userId
// should_runPaymentSaga_when_paymentSetup → POST /api/v1/profile/payment-setup with card body + userId
```
Write concrete assertions mirroring the existing `ProfileControllerTest` setup (reuse its `TestSecurityConfig` / mock `ProfileService`). Each test names the new endpoint and asserts status + body shape.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -f profile/pom.xml -Dtest=ProfileControllerTest test`
Expected: FAIL — old `register-*`/`get-by-username` endpoints gone or not yet renamed.

- [ ] **Step 3: Rewrite the controller** — remove `register-admin/worker/user`, `delete-user`, `password-reset`, `password-reset-token`, `set-new-password` (these now live in auth). Keep/replace:
  - `GET /me` → `profileService.getByUserId(authentication.getName())`
  - `PUT /me` → `profileService.updateProfile(authentication.getName(), cmd)`
  - `POST /payment-setup` → `profileService.setupPayment(authentication.getName(), cardCmd)`
  - `@PreAuthorize` expressions: drop username comparisons; gate by `hasRole('USER')` / scope as before, identity is the token subject.

- [ ] **Step 4: Refactor `ProfileServiceImpl`** — delete `createAdmin/createWorker/createUser/buildProfile/createStaff` (registration moved to auth) and `passwordReset/isPasswordResetTokenValid/setNewPassword/updateUser(byUsername)/deleteUser(byUsername)/getUserByUsername`. Add `getByUserId`, `updateProfile`, `setupPayment` (the latter reuses the existing `createPaymentCustomer` + `addCardToCustomer` private methods, keyed by userId). Remove now-unused `JwtService`/`MailService` wiring **only if** no remaining method uses them (the welcome-mail call moves: emit it from the registered-event listener, or keep notification entirely in the notification service — choose keep-in-notification to avoid profile re-adding a mail concern; `[ticket]`-note if deferred).

- [ ] **Step 5: Run the targeted tests, fix fallout**

Run: `./mvnw -f profile/pom.xml -Dtest=ProfileControllerTest,ProfileServiceTest test`
Expected: PASS. Update `ProfileControllerIntegrationTest` to the new endpoints; delete tests for removed flows.

- [ ] **Step 6: Full profile verify**

Run: `./mvnw -f profile/pom.xml clean verify && ./mvnw -f profile/pom.xml checkstyle:check`
Expected: BUILD SUCCESS, coverage gate met.

- [ ] **Step 7: Commit**

```bash
git add profile/src
git commit -m "refactor(profile): key endpoints by userId, remove auth-owned flows

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: Cutover — drop shared collection, seed via new flow, docs

**Files:**
- Modify: `mongo-init/` (remove shared `users` seed; the `profiles` collection is created on first insert)
- Modify: `docker-compose.yml` / `application-dev.yml` if either references the shared `users` collection
- Modify: `authentication/src/main/resources/db/migration/V2__insert_data.sql` only if it seeds users (it seeds OAuth clients — leave clients intact)
- Create: `docs/sagas/user-registration.md` (choreography flow doc, from the `_template.md`)
- Modify: `README.md` registration section if it documents `/profile/register-*`

- [ ] **Step 1:** Grep for stragglers referencing the old shape:

Run: `grep -rn "register-user\|register-admin\|register-worker\|get-by-username\|collection = \"users\"\|@Document(\"users\")" --include=*.java --include=*.yml --include=*.md --include=*.js .`
Expected: only intended references remain (auth `/api/v1/auth/register`, profile `/me`). Fix any leftover.

- [ ] **Step 2:** Update `mongo-init` so it no longer seeds the shared `users` collection; if a dev seed is wanted, document re-registering through `POST /api/v1/auth/register`.

- [ ] **Step 3:** Write `docs/sagas/user-registration.md` describing: client → `POST /auth/register` → credential persisted + `UserRegisteredEvent` → profile shell; then `POST /profile/payment-setup`; and deletion via `DELETE /auth/account` → `UserDeletedEvent` → profile soft-delete + payment cleanup. Note DLT topics and idempotency key (`userId`).

- [ ] **Step 4:** Update `decisions.md` (root) with a one-line entry: `[2026-06] auth/profile: split shared users collection — auth owns credentials (Postgres), profile owns personal data (Mongo), linked by userId via Kafka choreography.`

- [ ] **Step 5: Commit**

```bash
git add mongo-init docs/sagas/user-registration.md decisions.md README.md docker-compose.yml
git commit -m "chore: cut over to split user-data ownership; document registration saga

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: Cross-module verification

- [ ] **Step 1: Build both modules clean**

Run:
```bash
./mvnw -f authentication/pom.xml clean verify
./mvnw -f profile/pom.xml clean verify
```
Expected: BUILD SUCCESS for both.

- [ ] **Step 2: Lint + migration validate (auth)**

Run: `./mvnw -f authentication/pom.xml checkstyle:check flyway:validate`
Expected: no violations; migrations validate (V1–V4).

- [ ] **Step 3: Confirm no shared-collection references remain**

Run: `grep -rn "@Document(\"users\")\|collection = \"users\"\|AuthUser\|AuthUserDao" authentication/src profile/src`
Expected: no matches.

- [ ] **Step 4: Final review checklist (manual)**
  - Auth: `register` 201 + event; duplicate 409; `DELETE /account` soft-deletes + event; `loadUserByUsername` returns `sub=userId`, `email` claim present.
  - Profile: registered event → shell; deleted event → soft-delete + payment cleanup; malformed event → DLT; endpoints keyed by `userId`.
  - No field duplicated except `userId`; profile holds no password/roles.

---

## Self-review notes (coverage map)

- Spec §1 ownership split → Tasks 1, 2, 6.
- Spec §2 data model → Tasks 1 (auth), 6 (profile).
- Spec §3 registration choreography → Tasks 3, 4 (auth emit), 7 (profile consume), 9 (payment-setup).
- Spec §4 identity reads from JWT → Task 2 (`sub=userId`), Task 4 (`email` claim), Task 9 (endpoints).
- Spec §5 password flows → Task 5 (`set-new-password`; reset-token mailing flagged); deletion → Tasks 5 (auth emit), 8 (profile consume).
- Spec §6 cutover → Task 10.
- Spec §7 testing → ITs in Tasks 1–9; gates in Tasks 5, 9, 11.
