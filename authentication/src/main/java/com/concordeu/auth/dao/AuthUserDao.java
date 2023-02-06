package com.concordeu.auth.dao;

import com.concordeu.auth.domain.AuthUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthUserDao extends MongoRepository<AuthUser, String> {

    Optional<AuthUser> findByUsername(String username);

}
