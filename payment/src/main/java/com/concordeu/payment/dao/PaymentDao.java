package com.concordeu.payment.dao;

import com.concordeu.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentDao extends JpaRepository<Payment, String> {
}
