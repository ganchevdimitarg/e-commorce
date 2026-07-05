package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ItemDao;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Item;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderLineDto;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owns every order-related DB write so that each is its own transaction boundary. This is
 * the seam that lets {@code OrderServiceImpl.createOrder} run the external payment charge
 * <em>outside</em> any open transaction: the order is committed by {@link #placeOrder}
 * before the charge, and {@link #confirmPaid} / {@link #markPaymentFailed} each run in a
 * fresh transaction afterwards. Splitting these into a separate bean is deliberate —
 * self-invocation would not honour {@code @Transactional}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPersistence {

    private final OrderDao orderDao;
    private final ItemDao itemDao;
    private final OrderStatusHistoryDao statusHistoryDao;
    private final ChargeService chargeService;
    private final MeterRegistry meterRegistry;

    /**
     * Persist a new order in {@code PLACED} together with its line items, committing before
     * any payment is attempted. Returns the saved order (detached once the transaction
     * commits — callers should re-load via {@link #confirmPaid}/{@link #markPaymentFailed}
     * by order number rather than touching lazy associations).
     */
    @Transactional
    public Order placeOrder(OrderDto orderDto, String changedBy) {
        Order order = Order.builder()
                .username(orderDto.username())
                .deliveryComment(orderDto.deliveryComment())
                .orderNumber(orderDao.nextOrderNumber())
                .status(OrderStatus.PLACED)
                .createdOn(LocalDateTime.now())
                .build();
        Order saved = orderDao.saveAndFlush(order);
        recordHistory(saved, null, OrderStatus.PLACED, changedBy, "order placed");

        List<Item> items = orderDto.items().stream()
                .map(line -> Item.builder()
                        .productId(line.productId())
                        .quantity(line.quantity())
                        .order(saved)
                        .build())
                .toList();
        itemDao.saveAllAndFlush(items);

        meterRegistry.counter("order.order.created").increment();
        log.info("Order {} placed with {} item(s)", saved.getOrderNumber(), items.size());
        return saved;
    }

    /** Persist the successful charge and move the order to {@code PAID}, in one transaction. */
    @Transactional
    public void confirmPaid(long orderNumber, PaymentDto payment, String changedBy) {
        Order order = loadForUpdate(orderNumber);
        chargeService.saveCharge(order, payment);
        transition(order, OrderStatus.PAID, changedBy, "payment succeeded");
    }

    /** Mark an order {@code PAYMENT_FAILED} after a charge could not be captured or was refunded. */
    @Transactional
    public void markPaymentFailed(long orderNumber, String changedBy, String reason) {
        Order order = loadForUpdate(orderNumber);
        transition(order, OrderStatus.PAYMENT_FAILED, changedBy, reason);
    }

    /**
     * Apply a guarded status transition to an already-loaded order and record the history
     * row. Shared by the create/cancel/advance flows so the state machine is enforced in
     * exactly one place.
     */
    @Transactional
    public void transition(Order order, OrderStatus target, String changedBy, String reason) {
        OrderStatus from = order.getStatus();
        if (from != null && !from.canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot move order %d from %s to %s".formatted(order.getOrderNumber(), from, target));
        }
        order.setStatus(target);
        orderDao.saveAndFlush(order);
        recordHistory(order, from, target, changedBy, reason);
    }

    private Order loadForUpdate(long orderNumber) {
        return orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to,
                               String changedBy, String reason) {
        statusHistoryDao.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedBy)
                .reason(reason)
                .build());
    }
}
