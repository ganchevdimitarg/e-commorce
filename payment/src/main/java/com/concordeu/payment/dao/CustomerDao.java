package com.concordeu.payment.dao;

import com.concordeu.payment.domain.AppCustomer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerDao extends JpaRepository<AppCustomer, String> {
    @EntityGraph(value = "graph-payment-customer")
    Optional<AppCustomer> findByUsername(String username);
}
