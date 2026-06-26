package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import com.ganchevdimitarg.auth.dto.RegisterUserCommand;
import com.ganchevdimitarg.auth.dto.RegisterUserResponse;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import com.ganchevdimitarg.auth.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher publisher;

    @Transactional
    public RegisterUserResponse register(RegisterUserCommand cmd) {
        repository.findByEmailAndDeletedAtIsNull(cmd.email()).ifPresent(existing -> {
            throw new ConflictException("Email already registered: " + cmd.email());
        });

        UUID id = UUID.randomUUID();
        UserCredential credential = new UserCredential();
        credential.setId(id);
        credential.setEmail(cmd.email());
        credential.setPasswordHash(passwordEncoder.encode(cmd.password()));
        credential.setRoles(cmd.role().authorities());
        credential.setEnabled(true);
        repository.save(credential);

        publisher.publishRegistered(new UserRegisteredEvent(
                id.toString(), cmd.email(), cmd.role().authorities(),
                cmd.firstName(), cmd.lastName(), cmd.phoneNumber(),
                cmd.city(), cmd.street(), cmd.postCode(), Instant.now()));

        log.info("Registered user {} with role {}", cmd.email(), cmd.role());
        return new RegisterUserResponse(id.toString());
    }
}
