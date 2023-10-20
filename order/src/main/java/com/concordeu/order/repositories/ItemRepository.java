package com.concordeu.order.repositories;

import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Item;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ItemRepository extends ReactiveCrudRepository<Item, Long> {
    @Query("""
            SELECT *
            FROM items 
            WHERE order_id = :orderId
            """)
    Flux<Item> findItemsByOrderId(@Param("orderId") Long orderId);
}
