package com.concordeu.auth.repository;

import com.concordeu.auth.entities.AuthUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthUserRepository extends MongoRepository<AuthUser, String> {

    Optional<AuthUser> findByUsername(String username);

}
