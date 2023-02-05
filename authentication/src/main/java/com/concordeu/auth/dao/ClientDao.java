package com.concordeu.auth.dao;

import com.concordeu.auth.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientDao extends JpaRepository<Client, String> {

    @EntityGraph(value = "graph-auth-client")
    Optional<Client> findByClientId(String clientId);
}
