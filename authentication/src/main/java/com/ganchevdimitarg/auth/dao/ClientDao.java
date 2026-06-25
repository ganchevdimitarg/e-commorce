package com.ganchevdimitarg.auth.dao;

import com.ganchevdimitarg.auth.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientDao extends JpaRepository<Client, UUID> {

    Optional<Client> findByClientId(String clientId);
}
