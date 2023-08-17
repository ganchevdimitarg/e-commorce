package com.concordeu.client.repositories;

import com.concordeu.client.entities.OpaqueToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OpaqueTokenRepository extends MongoRepository<OpaqueToken, String> {
    Optional<OpaqueToken> findByToken(String token);
}
