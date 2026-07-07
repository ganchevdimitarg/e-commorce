package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.domain.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargeDao extends JpaRepository<AppCharge, String> {
    Optional<AppCharge> findByChargeId(String chargeId);
}
