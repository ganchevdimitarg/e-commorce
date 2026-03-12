package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.domain.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeDao extends JpaRepository<AppCharge, String> {
    AppCharge findByChargeId(String chargeId);
}
