package com.concordeu.payment.dao;

import com.concordeu.payment.domain.AppCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDao extends JpaRepository<AppCustomer, String> {
    AppCustomer findByEmail(String email);
}
