package com.concordeu.auth.repository;

import com.concordeu.auth.entities.RSA;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RSAKeyRepository extends MongoRepository<RSA, String> {

}
