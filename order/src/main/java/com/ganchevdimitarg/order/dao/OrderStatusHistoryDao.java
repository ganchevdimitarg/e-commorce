package com.ganchevdimitarg.order.dao;

import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryDao extends JpaRepository<OrderStatusHistory, String> {
    List<OrderStatusHistory> findByOrderOrderByCreatedAtAsc(Order order);
}
