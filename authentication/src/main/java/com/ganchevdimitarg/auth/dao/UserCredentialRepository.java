package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmailAndDeletedAtIsNull(String email);

    Optional<UserCredential> findByIdAndDeletedAtIsNull(UUID id);
}
