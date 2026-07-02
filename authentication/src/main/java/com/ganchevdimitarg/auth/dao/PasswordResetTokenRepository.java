package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndDeletedAtIsNull(String tokenHash);
}
