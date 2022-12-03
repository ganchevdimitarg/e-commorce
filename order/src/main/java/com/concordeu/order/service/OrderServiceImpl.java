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
    public OrderResponseDto getOrder(long orderNumber) {
        Order order = orderDao.findByOrderNumber(orderNumber).get();

        UserDto userInfo = webClient
                .get()
                .uri("http://127.0.0.1:8081/api/v1/profile/get-by-username/{username}", order.getUsername())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();

        ProductResponseDto productInfo = webClient
                .get()
                .uri("http://127.0.0.1:8081/api/v1/catalog/product/get-product/{productName}", order.getProductName())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .block();


        return new OrderResponseDto(userInfo, productInfo, order.getQuantity(), order.getDeliveryComment(), order.getCreatedOn());
    }

    private Order mapOrderDtoToOrder(OrderDto orderDto) {
        return Order.builder()
                .username(orderDto.username())
                .productName(orderDto.productName())
                .quantity(orderDto.quantity())
                .deliveryComment(orderDto.deliveryComment())
                .build();
    }
}
