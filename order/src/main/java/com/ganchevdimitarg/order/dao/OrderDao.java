package com.ganchevdimitarg.order.dao;

import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderDao extends JpaRepository<Order, String> {
    Optional<Order> findByOrderNumber(long orderNumber);

    Page<Order> findByUsername(String username, Pageable pageable);

    Page<Order> findByUsernameAndStatus(String username, OrderStatus status, Pageable pageable);
}
