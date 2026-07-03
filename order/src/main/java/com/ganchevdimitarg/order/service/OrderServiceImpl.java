package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ItemDao;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Item;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import com.ganchevdimitarg.order.dto.*;
import com.ganchevdimitarg.order.excaption.ConflictException;
import com.ganchevdimitarg.order.excaption.InvalidRequestDataException;
import com.ganchevdimitarg.order.excaption.NotFoundException;
import jakarta.annotation.PostConstruct;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    private long orderCounter;

    @PostConstruct
    public void init() {
        orderCounter = orderDao.count();
    }

    @Value("${catalog.service.products.get.uri}")
    private String catalogServiceGetProductsByIdsUri;
    @Value("${profile.service.get.uri}")
    private String profileServiceGetProfileByUsernameUri;
    @Value("${profile.service.post.uri}")
    private String profileServiceCreateUserUri;

    @Override
    @Transactional
    public void createOrder(OrderDto orderDto, String authenticationName) {
        String username = orderDto.username();
        if (!username.equals(authenticationName)) {
            logMessage(authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);
        if (userInfo.username().isEmpty()) {
            userInfo = createProfileUser(orderDto);
        }

        List<ProductResponseDto> products = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(orderDto.items()
                                .stream()
                                .map(Item::getProductId)
                                .toList())
                        .build()
        );

        long amount = Long.parseLong(
                products.stream()
                        .map(ProductResponseDto::price)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .toString()
                        .replace(".", "")
        );

        PaymentDto payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount);

        Order order = Order.builder()
                .username(orderDto.username())
                .deliveryComment(orderDto.deliveryComment())
                .orderNumber(++orderCounter)
                .status(OrderStatus.PLACED)
                .createdOn(LocalDateTime.now())
                .build();
        Order orderSave = orderDao.saveAndFlush(order);
        recordHistory(orderSave, null, OrderStatus.PLACED, authenticationName, "order placed");
        log.info("Order was successfully created");

        List<Item> items = orderDto.items();
        items.forEach(item -> item.setOrder(orderSave));
        itemDao.saveAllAndFlush(items);
        log.info("Items was successfully created");

        chargeService.saveCharge(orderSave, payment);
        applyTransition(orderSave, OrderStatus.PAID, authenticationName, "payment succeeded");
    }

    private static void logMessage(String authenticationName, String username) {
        log.debug("User '{}' try to access another account '{}'", authenticationName, username);
    }

    @Override
    public void deleteOrder(long orderNumber) {
        // load-then-delete so @SQLDelete soft-deletes (a derived deleteBy… bulk query
        // would bypass it and hard-delete)
        orderDao.findByOrderNumber(orderNumber).ifPresent(orderDao::delete);
        log.info("Order was successfully delete");
    }

    @Override
    public OrderResponseDto getOrder(long orderNumber, String authenticationName) {
        Optional<Order> order = orderDao.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String username = order.get().getUsername();
        if (!username.equals(authenticationName)) {
            logMessage(authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);

        checkAvailabilityOfCatalogService(userInfo.username());

        List<ProductResponseDto> productInfo = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(order.get()
                                .getItems()
                                .stream()
                                .map(Item::getProductId)
                                .toList())
                        .build()
        );

        return OrderResponseDto.builder()
                .userInfo(userInfo)
                .productInfo(productInfo)
                .orderNumber(order.get().getOrderNumber())
                .deliveryComment(order.get().getDeliveryComment())
                .createdOn(order.get().getCreatedOn())
                .build();
    }

    private List<ProductResponseDto> getRequestToCategoryServiceProductInfo(ItemRequestDto request) {
        List<ProductResponseDto> responseDtoList = circuitBreakerFactory.create("orderService").run(
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
                    return List.of(ProductResponseDto.builder().name("").build());
                });

        assert responseDtoList != null;
        checkAvailabilityOfCatalogService(responseDtoList.get(0).name());
        return responseDtoList;
    }

    private void checkAvailabilityOfCatalogService(String token) {
        if (token.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the order service.
                    Please check the request details again
                    """);
        }
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
