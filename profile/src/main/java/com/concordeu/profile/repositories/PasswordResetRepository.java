package com.concordeu.profile.repositories;

import com.concordeu.profile.entities.PasswordReset;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import java.util.Optional;

public interface PasswordResetRepository extends ReactiveMongoRepository<PasswordReset, String> {
    Optional<PasswordReset> findByToken(String token);
}
