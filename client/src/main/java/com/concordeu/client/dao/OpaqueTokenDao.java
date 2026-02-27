package com.ganchevdimitarg.client.dao;

import com.ganchevdimitarg.client.domain.OpaqueToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OpaqueTokenDao extends MongoRepository<OpaqueToken, String> {
    Optional<OpaqueToken> findByToken(String token);
}
