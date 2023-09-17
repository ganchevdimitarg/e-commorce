package com.concordeu.payment.repositories;

import com.concordeu.payment.domain.AppCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<AppCharge, String> {
    AppCharge findByChargeId(String chargeId);
}
