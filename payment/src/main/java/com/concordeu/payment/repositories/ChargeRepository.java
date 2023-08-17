package com.concordeu.payment.repositories;

import com.concordeu.payment.entities.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<AppCharge, String> {
    AppCharge findByChargeId(String chargeId);
}
