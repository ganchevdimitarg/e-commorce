package com.concordeu.auth.dao;

import com.concordeu.auth.domain.AuthUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthUserDao extends MongoRepository<AuthUser, String> {
    Optional<AuthUser> findByUsername(String username);

}
