package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ItemDao;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Item;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import com.ganchevdimitarg.order.dto.*;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import com.ganchevdimitarg.order.exception.NotFoundException;
import com.ganchevdimitarg.order.exception.ValidationException;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderDao orderDao;
    private final ItemDao itemDao;
    private final RestClient restClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ChargeService chargeService;
    private final OrderStatusHistoryDao statusHistoryDao;
    private final MeterRegistry meterRegistry;

    @Value("${catalog.service.products.get.uri}")
    private String catalogServiceGetProductsByIdsUri;
    @Value("${profile.service.get.uri}")
    private String profileServiceGetProfileByUsernameUri;
    @Value("${profile.service.post.uri}")
    private String profileServiceCreateUserUri;

    @Override
    @Transactional
    public OrderCreatedResponse createOrder(OrderDto orderDto, String authenticationName) {
        String username = orderDto.username();
        if (!username.equals(authenticationName)) {
            logMessage(authenticationName, username);
            throw new NotFoundException("Order not found for user " + authenticationName);
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);
        if (userInfo.username().isEmpty()) {
            userInfo = createProfileUser(orderDto);
        }

        List<ProductResponseDto> products = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(orderDto.items().stream().map(OrderLineDto::productId).toList())
                        .build());

        long amount = computeAmountInCents(orderDto.items(), products);

        PaymentDto payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount);

        Order order = Order.builder()
                .username(orderDto.username())
                .deliveryComment(orderDto.deliveryComment())
                .orderNumber(orderDao.nextOrderNumber())
                .status(OrderStatus.PLACED)
                .createdOn(LocalDateTime.now())
                .build();
        Order orderSave = orderDao.saveAndFlush(order);
        recordHistory(orderSave, null, OrderStatus.PLACED, authenticationName, "order placed");
        meterRegistry.counter("order.order.created").increment();
        log.info("Order was successfully created");

        List<Item> items = orderDto.items().stream()
                .map(line -> Item.builder()
                        .productId(line.productId())
                        .quantity(line.quantity())
                        .order(orderSave)
                        .build())
                .toList();
        itemDao.saveAllAndFlush(items);
        log.info("Items was successfully created");

        chargeService.saveCharge(orderSave, payment);
        applyTransition(orderSave, OrderStatus.PAID, authenticationName, "payment succeeded");
        return new OrderCreatedResponse(orderSave.getOrderNumber());
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
                throw new ValidationException(
                        "No price for product " + line.productId());
            }
            total = total.add(price.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static void logMessage(String authenticationName, String username) {
        log.debug("User '{}' try to access another account '{}'", authenticationName, username);
    }

    @Override
    @Transactional
    public void deleteOrder(long orderNumber, String username) {
        // load-then-delete so @SQLDelete soft-deletes (a derived deleteBy… bulk query
        // would bypass it and hard-delete)
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (!order.getUsername().equals(username)) {
            logMessage(username, order.getUsername());
            throw new NotFoundException("Order not found: " + orderNumber);
        }
        orderDao.delete(order);
        log.info("Order was successfully deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrder(long orderNumber, String authenticationName) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));

        String username = order.getUsername();
        if (!username.equals(authenticationName)) {
            logMessage(authenticationName, username);
            throw new NotFoundException("Order not found: " + orderNumber);
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);

        List<ProductResponseDto> productInfo = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(order.getItems()
                                .stream()
                                .map(Item::getProductId)
                                .toList())
                        .build()
        );

        return OrderResponseDto.builder()
                .userInfo(userInfo)
                .productInfo(productInfo)
                .orderNumber(order.getOrderNumber())
                .deliveryComment(order.getDeliveryComment())
                .createdOn(order.getCreatedOn())
                .build();
    }

    private List<ProductResponseDto> getRequestToCategoryServiceProductInfo(ItemRequestDto request) {
        List<ProductResponseDto> products = circuitBreakerFactory.create("orderService").run(
                () -> restClient
                        .post()
                        .uri(catalogServiceGetProductsByIdsUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                        }),
                throwable -> {
                    log.warn("Catalog Server is down", throwable);
                    return List.of();
                });

        if (products == null || products.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the order service.
                    Please check the request details again
                    """);
        }
        return products;
    }

    private UserDto getRequestToProfileServiceUserInfo(String username) {
        return circuitBreakerFactory.create("orderService").run(
                () -> restClient
                        .get()
                        .uri(profileServiceGetProfileByUsernameUri + username)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(UserDto.class),
                throwable -> {
                    log.warn("Profile Server is down", throwable);
                    return UserDto.builder().username("").build();
                });
    }

    private UserDto createProfileUser(OrderDto orderDto) {
        UserDto profileRequest = UserDto.builder()
                .username(orderDto.username())
                .password("opaque")
                .firstName(orderDto.firstName())
                .lastName(orderDto.lastName())
                .phoneNumber(orderDto.phoneNumber())
                .city(orderDto.city())
                .street(orderDto.street())
                .postCode(orderDto.postCode())
                .cardNumber(orderDto.cardNumber())
                .cardExpMonth(orderDto.cardExpMonth())
                .cardExpYear(orderDto.cardExpYear())
                .cardCvc(orderDto.cardCvc())
                .build();

        return circuitBreakerFactory.create("orderService").run(
                () -> restClient
                        .post()
                        .uri(profileServiceCreateUserUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(profileRequest)
                        .retrieve()
                        .body(UserDto.class),
                throwable -> {
                    log.warn("Profile Server is down", throwable);
                    return UserDto.builder().username("").build();
                });
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

    private void applyTransition(Order order, OrderStatus target, String changedBy, String reason) {
        OrderStatus from = order.getStatus();
        if (from != null && !from.canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot move order %d from %s to %s".formatted(order.getOrderNumber(), from, target));
        }
        order.setStatus(target);
        orderDao.saveAndFlush(order);
        recordHistory(order, from, target, changedBy, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderTrackingResponse getTracking(long orderNumber, String username) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (!order.getUsername().equals(username)) {
            logMessage(username, order.getUsername());
            throw new NotFoundException("Order not found: " + orderNumber);
        }
        List<StatusHistoryEntry> history = statusHistoryDao.findByOrderOrderByCreatedAtAsc(order)
                .stream()
                .map(h -> new StatusHistoryEntry(h.getFromStatus(), h.getToStatus(),
                        h.getChangedBy(), h.getReason(), h.getCreatedAt()))
                .toList();
        return new OrderTrackingResponse(order.getOrderNumber(), order.getStatus(), history);
    }

    @Override
    @Transactional
    public void cancelOrder(long orderNumber, String username, String reason) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (!order.getUsername().equals(username)) {
            logMessage(username, order.getUsername());
            throw new NotFoundException("Order not found: " + orderNumber);
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new ConflictException(
                    "Order %d cannot be cancelled from status %s"
                            .formatted(orderNumber, order.getStatus()));
        }
        if (order.getStatus() == OrderStatus.PAID && order.getCharge() != null) {
            chargeService.refund(order.getCharge().getChargeId(), 0L, username);
        }
        applyTransition(order, OrderStatus.CANCELLED, username, reason);
    }

    @Override
    @Transactional
    public void advanceStatus(long orderNumber, OrderStatus target, String changedBy, String reason) {
        Order order = orderDao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
        if (target == OrderStatus.CANCELLED) {
            throw new ConflictException(
                    "Use the cancel endpoint to cancel order %d; status advance cannot cancel"
                            .formatted(orderNumber));
        }
        applyTransition(order, target, changedBy, reason);
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
