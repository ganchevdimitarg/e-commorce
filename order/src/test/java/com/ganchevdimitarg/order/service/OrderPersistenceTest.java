package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ItemDao;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderLineDto;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.NotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceTest {

    @Mock
    private OrderDao orderDao;
    @Mock
    private ItemDao itemDao;
    @Mock
    private OrderStatusHistoryDao statusHistoryDao;
    @Mock
    private ChargeService chargeService;

    private OrderPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new OrderPersistence(orderDao, itemDao, statusHistoryDao,
                chargeService, new SimpleMeterRegistry());
    }

    @Test
    void should_persistPlacedOrderWithItemsAndHistory_when_placingOrder() {
        when(orderDao.nextOrderNumber()).thenReturn(42L);
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderDto dto = OrderDto.builder().username("john").deliveryComment("door")
                .items(List.of(new OrderLineDto("p_1", 2))).build();

        Order saved = persistence.placeOrder(dto, "john");

        assertThat(saved.getOrderNumber()).isEqualTo(42L);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PLACED);
        verify(itemDao).saveAllAndFlush(any());
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(statusHistoryDao).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void should_saveChargeAndMovePaid_when_confirmingPayment() {
        Order order = Order.builder().orderNumber(1).username("john").status(OrderStatus.PLACED).build();
        when(orderDao.findByOrderNumber(1)).thenReturn(Optional.of(order));
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        persistence.confirmPaid(1, payment, "john");

        verify(chargeService).saveCharge(order, payment);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(statusHistoryDao).save(any(OrderStatusHistory.class));
    }

    @Test
    void should_moveToPaymentFailed_when_markingFailed() {
        Order order = Order.builder().orderNumber(2).username("john").status(OrderStatus.PLACED).build();
        when(orderDao.findByOrderNumber(2)).thenReturn(Optional.of(order));
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        persistence.markPaymentFailed(2, "john", "charge failed");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(statusHistoryDao).save(any(OrderStatusHistory.class));
    }

    @Test
    void should_throwNotFound_when_confirmingMissingOrder() {
        when(orderDao.findByOrderNumber(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistence.confirmPaid(9, PaymentDto.builder().build(), "john"))
                .isInstanceOf(NotFoundException.class);
        verify(chargeService, never()).saveCharge(any(), any());
    }

    @Test
    void should_rejectTransition_when_illegal() {
        Order order = Order.builder().orderNumber(3).username("john").status(OrderStatus.PAID).build();

        assertThatThrownBy(() -> persistence.transition(order, OrderStatus.DELIVERED, "ops", null))
                .isInstanceOf(ConflictException.class);
        verify(statusHistoryDao, never()).save(any());
    }

    @Test
    void should_recordHistory_when_transitionLegal() {
        Order order = Order.builder().orderNumber(4).username("john").status(OrderStatus.PAID).build();
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        persistence.transition(order, OrderStatus.SHIPPED, "ops", "left warehouse");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(statusHistoryDao, times(1)).save(any(OrderStatusHistory.class));
    }
}
