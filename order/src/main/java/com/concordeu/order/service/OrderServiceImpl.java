package com.concordeu.order.service;

import com.concordeu.order.dao.OrderDao;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.dto.ProductResponseDto;
import com.concordeu.order.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderDao orderDao;
    private final WebClient webClient;

    @Override
    public void createOrder(OrderDto orderDto) {
        Order entity = mapOrderDtoToOrder(orderDto);
        entity.setCreatedOn(LocalDateTime.now());
        entity.setOrderNumber(orderDao.count() + 1);
        orderDao.saveAndFlush(entity);
        log.info("Order was successfully created");
    }

    @Override
    public void deleteOrder(long orderNumber) {
        orderDao.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");
    }

    @Override
    public OrderResponseDto getOrder(long orderNumber, String authorization) {
        Optional<Order> order = orderDao.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String base_uri = "http://127.0.0.1:8081/api/v1";
        String headerAuthorization = "Authorization";
        UserDto userInfo = webClient
                .get()
                .uri(base_uri + "/profile/get-by-username?username={username}", order.get().getUsername())
                .header(headerAuthorization, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();

        ProductResponseDto productInfo = webClient
                .get()
                .uri(base_uri + "/catalog/product/get-product-id?productId={productId}", order.get().getProductId())
                .header(headerAuthorization, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .block();


        return new OrderResponseDto(
                userInfo,
                productInfo,
                order.get().getQuantity(),
                order.get().getDeliveryComment(),
                order.get().getCreatedOn()
        );
    }

    private Order mapOrderDtoToOrder(OrderDto orderDto) {
        return Order.builder()
                .username(orderDto.username())
                .productId(orderDto.productId())
                .productName(orderDto.productName())
                .quantity(orderDto.quantity())
                .deliveryComment(orderDto.deliveryComment())
                .build();
    }
}
