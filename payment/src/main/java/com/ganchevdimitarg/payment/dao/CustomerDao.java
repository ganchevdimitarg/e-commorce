package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.domain.AppCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerDao extends JpaRepository<AppCustomer, String> {
    Optional<AppCustomer> findByUsername(String username);
}
