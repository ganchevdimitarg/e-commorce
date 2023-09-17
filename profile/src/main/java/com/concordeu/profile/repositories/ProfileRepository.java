package com.concordeu.profile.repositories;

import com.concordeu.profile.entities.Profile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ProfileRepository extends ReactiveMongoRepository<Profile, String> {
    Mono<Profile> findByUsername(String username);
}
