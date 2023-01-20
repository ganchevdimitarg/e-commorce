package com.concordeu.payment.dao;

import com.concordeu.payment.domain.AppCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardDao extends JpaRepository<AppCard, String> {
}
