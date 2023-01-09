package com.concordeu.profile.dao;

import com.concordeu.profile.domain.PasswordReset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetDao extends MongoRepository<PasswordReset, String> {
    Optional<PasswordReset> findByToken(String token);
}
