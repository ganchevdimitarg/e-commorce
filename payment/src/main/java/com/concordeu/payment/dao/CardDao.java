package com.concordeu.payment.dao;

import com.concordeu.payment.domain.AppCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardDao extends JpaRepository<AppCard, String> {
    List<AppCard> findAppCardsByCustomerId(String customerId);
}
