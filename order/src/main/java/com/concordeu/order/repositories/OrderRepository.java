package com.concordeu.order.repositories;

import com.concordeu.order.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {
    @EntityGraph(value = "graph-order")
    Optional<Order> findByOrderNumber(long orderNumber);

    @Transactional
    void deleteByOrderNumber(long orderNumber);
}
