package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Item;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.dto.ItemRequestDto;
import com.ganchevdimitarg.order.dto.OrderCreatedResponse;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderLineDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.OrderTrackingResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.dto.ProductResponseDto;
import com.ganchevdimitarg.order.dto.StatusHistoryEntry;
import com.ganchevdimitarg.order.dto.UserDto;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import com.ganchevdimitarg.order.exception.NotFoundException;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
import com.ganchevdimitarg.order.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String PROFILE_CIRCUIT_BREAKER = "order-profile";
    private static final String CATALOG_CIRCUIT_BREAKER = "order-catalog";

    private final OrderDao orderDao;
    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ChargeService chargeService;
    private final OrderStatusHistoryDao statusHistoryDao;
    private final OrderPersistence orderPersistence;

    @Value("${catalog.service.products.get.uri}")
    private String catalogServiceGetProductsByIdsUri;
    @Value("${profile.service.get.uri}")
    private String profileServiceGetProfileByUsernameUri;

    /**
     * Create an order. Persistence and the external charge are deliberately <em>not</em> in
     * one transaction: the order is committed first, then the card is charged, then the
     * order is confirmed in a fresh transaction. If confirmation fails after a successful
     * charge the customer is refunded and the order is marked {@code PAYMENT_FAILED}, so no
     * path can take money without either a confirmed order or a refund.
     */
    @Override
    public OrderCreatedResponse createOrder(OrderDto orderDto, String authenticationName) {
        if (!orderDto.username().equals(authenticationName)) {
            log.debug("User '{}' tried to order for another account '{}'",
                    authenticationName, orderDto.username());
            throw new NotFoundException("Order not found for user " + authenticationName);
        }

        UserDto userInfo = getProfile(authenticationName);
        if (userInfo == null || userInfo.username() == null || userInfo.username().isBlank()) {
            throw new ConflictException("No profile for " + authenticationName
                    + "; register and add a card before ordering");
        }

        List<ProductResponseDto> products = getProducts(ItemRequestDto.builder()
                .items(orderDto.items().stream().map(OrderLineDto::productId).toList())
                .build());
        long amount = computeAmountInCents(orderDto.items(), products);

        // 1. Commit the order before any money moves, so it survives a payment failure.
        long orderNumber = orderPersistence.placeOrder(orderDto, authenticationName).getOrderNumber();

        // 2. Charge outside any transaction. A failure here means no money moved.
        PaymentDto payment;
        try {
            payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount, String.valueOf(orderNumber));
        } catch (RuntimeException e) {
            log.warn("Charge failed for order {}", orderNumber, e);
            orderPersistence.markPaymentFailed(orderNumber, authenticationName, "charge failed");
            throw e;
        }

        // 3. Confirm in a fresh transaction. If this fails the customer is already charged,
        //    so compensate with a refund before marking the order failed.
        try {
            orderPersistence.confirmPaid(orderNumber, payment, authenticationName);
        } catch (RuntimeException e) {
            log.error("Order {} confirmation failed after a successful charge; refunding", orderNumber, e);
            safeRefund(payment, authenticationName);
            orderPersistence.markPaymentFailed(orderNumber, authenticationName,
                    "confirmation failed after charge");
            throw e;
        }

        return new OrderCreatedResponse(orderNumber);
    }

    /**
     * Best-effort compensating refund. A refund that itself fails must not mask the original
     * failure — it is logged at error for alerting and reconciliation.
     */
    private void safeRefund(PaymentDto payment, String username) {
        try {
            chargeService.refund(payment.chargeId(), username);
        } catch (RuntimeException e) {
            log.error("COMPENSATION FAILED: could not refund charge {} for {}",
                    payment.chargeId(), username, e);
        }
    }

    /**
     * Sum of {@code price × quantity} across the order's line items, expressed in integer
     * cents for the payment API. Prices are matched to line items by product id; a line with
     * no matching product price is a bad request.
     */
    private static long computeAmountInCents(List<OrderLineDto> lines, List<ProductResponseDto> products) {
        Map<String, BigDecimal> priceById = products.stream()
                .filter(p -> p.id() != null && p.price() != null)
                .collect(Collectors.toMap(ProductResponseDto::id, ProductResponseDto::price,
                        (a, b) -> a));

        BigDecimal total = BigDecimal.ZERO;
        for (OrderLineDto line : lines) {
            BigDecimal price = priceById.get(line.productId());
            if (price == null) {
                throw new ValidationException("No price for product " + line.productId());
            }
            total = total.add(price.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    @Transactional
    public void deleteOrder(long orderNumber, String username) {
        // load-then-delete so @SQLDelete soft-deletes (a derived deleteBy… bulk query
        // would bypass it and hard-delete)
        orderDao.delete(loadOwnedOrder(orderNumber, username));
        log.info("Order {} soft-deleted", orderNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrder(long orderNumber, String authenticationName) {
        Order order = loadOwnedOrder(orderNumber, authenticationName);

        UserDto userInfo = getProfile(authenticationName);
        List<ProductResponseDto> productInfo = getProducts(ItemRequestDto.builder()
                .items(order.getItems().stream().map(Item::getProductId).toList())
                .build());

        return OrderResponseDto.builder()
                .userInfo(userInfo)
                .productInfo(productInfo)
                .orderNumber(order.getOrderNumber())
                .deliveryComment(order.getDeliveryComment())
                .createdOn(order.getCreatedOn())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(String username, OrderStatus status,
                                                            Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderDao.findByUsername(username, pageable)
                : orderDao.findByUsernameAndStatus(username, status, pageable);
        return PageResponse.of(page.map(o -> new OrderSummaryResponse(
                o.getOrderNumber(), o.getStatus(), o.getDeliveryComment(), o.getCreatedOn())));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderTrackingResponse getTracking(long orderNumber, String username) {
        Order order = loadOwnedOrder(orderNumber, username);
        List<StatusHistoryEntry> history = statusHistoryDao.findByOrderOrderByCreatedAtAsc(order)
                .stream()
                .map(h -> new StatusHistoryEntry(h.getFromStatus(), h.getToStatus(),
                        h.getChangedBy(), h.getReason(), h.getCreatedAt()))
                .toList();
        return new OrderTrackingResponse(order.getOrderNumber(), order.getStatus(), history);
    }

    /**
     * Cancel an order, refunding first if it was already paid. The refund is a network call to
     * the payment service and is deliberately made <em>outside</em> any transaction — the order
     * (with its charge) is loaded in a read-only transaction, the refund runs with no
     * transaction open, then the {@code CANCELLED} transition commits in a fresh one. This
     * avoids holding a DB row lock across the payment round-trip.
     */
    @Override
    public void cancelOrder(long orderNumber, String username, String reason) {
        Order order = orderPersistence.loadOwnedWithCharge(orderNumber, username);
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new ConflictException("Order %d cannot be cancelled from status %s"
                    .formatted(orderNumber, order.getStatus()));
        }
        if (order.getStatus() == OrderStatus.PAID && order.getCharge() != null) {
            chargeService.refund(order.getCharge().getChargeId(), username);
        }
        orderPersistence.transition(order, OrderStatus.CANCELLED, username, reason);
    }

    @Override
    @Transactional
    public void advanceStatus(long orderNumber, OrderStatus target, String changedBy, String reason) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (target == OrderStatus.CANCELLED || target == OrderStatus.PAYMENT_FAILED) {
            throw new ConflictException(
                    "Use the cancel/payment flow for order %d; status advance cannot set %s"
                            .formatted(orderNumber, target));
        }
        orderPersistence.transition(order, target, changedBy, reason);
    }

    /** Load an order and assert the caller owns it, masking foreign orders as 404. */
    private Order loadOwnedOrder(long orderNumber, String username) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (!order.getUsername().equals(username)) {
            log.debug("User '{}' tried to access order {} owned by '{}'",
                    username, orderNumber, order.getUsername());
            throw new NotFoundException("Order not found: " + orderNumber);
        }
        return order;
    }

    private UserDto getProfile(String username) {
        return callDependency("Profile service", PROFILE_CIRCUIT_BREAKER, () -> restClient
                .get()
                .uri(profileServiceGetProfileByUsernameUri + username)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(UserDto.class));
    }

    private List<ProductResponseDto> getProducts(ItemRequestDto request) {
        List<ProductResponseDto> products = callDependency("Catalog service", CATALOG_CIRCUIT_BREAKER, () -> restClient
                .post()
                .uri(catalogServiceGetProductsByIdsUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProductResponseDto>>() { }));

        if (products == null || products.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the order service.
                    Please check the request details again
                    """);
        }
        return products;
    }

    /**
     * Run an outbound call behind its dependency's own circuit breaker — one breaker per
     * downstream service, so a payment outage cannot trip calls to catalog or profile. A
     * tripped breaker or a failed call surfaces as a 503 {@link ServiceUnavailableException} —
     * never a silent empty sentinel that downstream code would mistake for a valid
     * "not found" result.
     */
    private <T> T callDependency(String dependencyName, String circuitBreakerId, Supplier<T> call) {
        return circuitBreakerFactory.create(circuitBreakerId).run(call::get, throwable -> {
            log.warn("{} unavailable", dependencyName, throwable);
            throw new ServiceUnavailableException(dependencyName + " is unavailable");
        });
    }
}
