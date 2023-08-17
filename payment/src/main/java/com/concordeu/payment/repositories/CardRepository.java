package com.concordeu.payment.repositories;

import com.concordeu.payment.entities.AppCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardRepository extends JpaRepository<AppCard, String> {
    List<AppCard> findAppCardsByCustomerId(String customerId);
}
