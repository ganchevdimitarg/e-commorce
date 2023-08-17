package com.concordeu.auth.repository;

import com.concordeu.auth.entities.Client;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findByClientId(String clientId);

}
