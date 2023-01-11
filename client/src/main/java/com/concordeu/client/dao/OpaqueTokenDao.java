package com.concordeu.client.dao;

import com.concordeu.client.domain.OpaqueToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OpaqueTokenDao extends MongoRepository<OpaqueToken, String> {
    Optional<OpaqueToken> findByToken(String token);
}
