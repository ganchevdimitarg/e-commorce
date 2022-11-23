package com.concordeu.order;

import com.concordeu.order.dao.OrderDao;
import com.concordeu.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Simple implements CommandLineRunner {
    private final OrderDao orderDao;

    @Override
    public void run(String... args) throws Exception {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Order order = Order.builder()
                    .orderNumber(i+1)
                    .username("example" + i + "@gmail.com")
                    .productName("product1")
                    .quantity(1)
                    .createdOn(LocalDateTime.now())
                    .deliveryComment("")
                    .build();
            orders.add(order);
        }

        orderDao.saveAllAndFlush(orders);
    }
}
