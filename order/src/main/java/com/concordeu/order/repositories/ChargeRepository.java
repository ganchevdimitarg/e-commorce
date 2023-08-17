package com.concordeu.order.repositories;

import com.concordeu.order.entities.Charge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<Charge, String> {
}
