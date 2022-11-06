package com.concordeu.auth.dao;

import com.concordeu.auth.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthUserDao extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);

}
