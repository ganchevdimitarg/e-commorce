package com.ganchevdimitarg.profile.dao;

import com.ganchevdimitarg.profile.domain.Profile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ProfileDao extends ReactiveMongoRepository<Profile, String> {

    Mono<Profile> findByUsername(String username);
}