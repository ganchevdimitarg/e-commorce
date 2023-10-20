package com.concordeu.order.repositories;

import com.concordeu.order.domain.Charge;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ChargeRepository extends ReactiveCrudRepository<Charge, Long> {
    @Query("""
            SELECT *
            FROM charges 
            WHERE order_id = :orderId
            """)
    Mono<Charge> findChargeByOrderId(@Param("orderId") Long orderId);
}
