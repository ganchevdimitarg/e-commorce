package com.concordeu.payment.dao;

import com.concordeu.payment.domain.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeDao extends JpaRepository<AppCharge, String> {
}
