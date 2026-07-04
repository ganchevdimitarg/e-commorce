package com.ganchevdimitarg.order;

import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private OrderDao orderDao;

    @Test
    void should_persistAndSoftDelete_andAssignSequentialNumbers() {
        long n1 = orderDao.nextOrderNumber();
        long n2 = orderDao.nextOrderNumber();
        assertThat(n2).isGreaterThan(n1);

        Order order = Order.builder()
                .orderNumber(n1).status(OrderStatus.PLACED).username("john").build();
        orderDao.saveAndFlush(order);

        assertThat(orderDao.findByOrderNumber(n1)).isPresent();

        orderDao.delete(order); // @SQLDelete -> soft delete
        orderDao.flush();
        assertThat(orderDao.findByOrderNumber(n1)).isEmpty(); // @SQLRestriction hides it
    }
}
