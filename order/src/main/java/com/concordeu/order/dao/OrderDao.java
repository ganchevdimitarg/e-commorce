package com.concordeu.order.dao;

import com.concordeu.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OrderDao extends JpaRepository<Order, String> {

    Optional<Order> findByOrderNumber(long orderNumber);

    @Transactional
    void deleteByOrderNumber(long orderNumber);
}
