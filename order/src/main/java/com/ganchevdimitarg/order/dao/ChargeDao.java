package com.ganchevdimitarg.order.dao;

import com.ganchevdimitarg.order.domain.Charge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeDao extends JpaRepository<Charge, String> {
}
