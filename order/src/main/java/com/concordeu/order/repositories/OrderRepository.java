package com.concordeu.order.repositories;

import com.concordeu.order.domain.Order;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    Mono<Order> findOrderById(Long orderId);

    Mono<Void> deleteOrderById(Long orderId);
}
