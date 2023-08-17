package com.concordeu.profile.repositories;

import com.concordeu.profile.entities.PasswordReset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetRepository extends MongoRepository<PasswordReset, String> {
    Optional<PasswordReset> findByToken(String token);
}
