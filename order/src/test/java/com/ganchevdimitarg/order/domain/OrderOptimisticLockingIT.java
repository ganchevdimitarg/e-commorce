package com.ganchevdimitarg.order.domain;

import com.ganchevdimitarg.order.AbstractIntegrationTest;
import com.ganchevdimitarg.order.dao.OrderDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderOptimisticLockingIT extends AbstractIntegrationTest {

    @Autowired
    private OrderDao orderDao;

    @Test
    void should_rejectStaleWrite_when_orderModifiedConcurrently() {
        Order order = Order.builder()
                .orderNumber(999_001L)
                .status(OrderStatus.PLACED)
                .username("optimistic-lock-it")
                .build();
        long orderNumber = orderDao.saveAndFlush(order).getOrderNumber();

        Order first = orderDao.findByOrderNumber(orderNumber).orElseThrow();
        Order second = orderDao.findByOrderNumber(orderNumber).orElseThrow();

        first.setDeliveryComment("first writer");
        orderDao.saveAndFlush(first);

        second.setDeliveryComment("second writer");
        assertThatThrownBy(() -> orderDao.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
