package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
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
        c.setDeletedAt(Instant.now());
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
