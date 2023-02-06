package com.concordeu.order.dao;

import com.concordeu.order.domain.Charge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeDao extends JpaRepository<Charge, String> {
}
