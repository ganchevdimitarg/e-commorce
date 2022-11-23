package com.concordeu.order.service;

import com.concordeu.order.dao.OrderDao;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.dto.ProductResponseDto;
import com.concordeu.order.dto.UserDto;
import com.concordeu.order.mapper.MapStructMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderDao orderDao;
    private final MapStructMapper mapper;
    private final WebClient webClient;

    @Override
    public void createOrder(OrderDto orderDto) {
        orderDao.saveAndFlush(mapper.mapOrderDtoToOrder(orderDto));
        log.info("Order was successfully created");
    }

    @Override
    public OrderDto deleteOrder(long orderNumber) {
        Order order = orderDao.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");

        return mapper.mapOrderToOrderDto(order);
    }

    @Override
    public OrderResponseDto getOrder(long orderNumber) {
        Order order = orderDao.findByOrderNumber(orderNumber).get();

        UserDto userInfo = webClient
                .post()
                .uri("http://localhost:8084/api/v1/profile/get-by-username/{username}", order.getUsername())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();

        ProductResponseDto productInfo = webClient
                .post()
                .uri("http://localhost:8083/api/v1/catalog/product//get-product/{productName}", order.getProductName())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .block();


        return new OrderResponseDto(userInfo, productInfo, order.getQuantity(), order.getDeliveryComment(), order.getCreatedOn());
    }
}
