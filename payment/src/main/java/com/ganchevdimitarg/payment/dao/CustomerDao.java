package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.domain.AppCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerDao extends JpaRepository<AppCustomer, String> {

    @Query("SELECT c FROM Customers c WHERE c.username = :username AND c.deletedAt IS NULL")
    Optional<AppCustomer> findByUsername(@Param("username") String username);
}
