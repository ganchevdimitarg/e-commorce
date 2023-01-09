package com.concordeu.profile.dao;

import com.concordeu.profile.domain.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProfileDao extends MongoRepository<Profile, String> {
    Optional<Profile> findByUsername(String username);
}
