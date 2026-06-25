package com.ganchevdimitarg.profile.dao;

import com.ganchevdimitarg.profile.domain.PasswordReset;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PasswordResetDao extends ReactiveMongoRepository<PasswordReset, String> {

    Mono<PasswordReset> findByToken(String token);

    Mono<PasswordReset> findByUsername(String username);

    Flux<PasswordReset> findAllByUsername(String username);
}