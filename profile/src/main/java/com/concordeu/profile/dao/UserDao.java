package com.concordeu.profile.dao;

import com.concordeu.profile.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface UserDao extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
}
