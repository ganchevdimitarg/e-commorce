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
                .uri("http://localhost:8083/api/v1/profile/get-by-username?username={username}", order.getUsername())
                .header("Authorization","Bearer eyJraWQiOiJkMWY5MDU4My1kZTYwLTQ5ZDEtYjQ1Yy00ZTQyNDQzMGMyYTYiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImdhdGV3YXkiLCJuYmYiOjE2NzAxNzI3MjMsInNjb3BlIjpbImF1dGgudXNlciIsIm9wZW5pZCJdLCJpc3MiOiJodHRwOlwvXC9sb2NhbGhvc3Q6ODA4MiIsImV4cCI6MTY3MDE3MzMyMywiaWF0IjoxNjcwMTcyNzIzfQ.Bgx0jvSksVY2pqGB2zlCSI-Yyo6-rsBDwwf6LpL7DCSt7FMaYEbvvOtxjsQmogk5so0a282ctNcbXRcFPNS6k0udAlDARqS2pxR2YgHUeOtr0tJvt1FbQbB2T7bi_qthgcS7P6tXKGSmtyPhNfeKIhrp8EqPNeDQm9eKQxS3H_0-RmWZOUqSvSP6FXqJ7OccSUiiIFgAXLPaWkDclQaXekp5i4csvp-HGH2ven0EhFItk2W8YLYGX0fiD-cM8LrGhCd8qwUW2MwpIzFYpOZVI6bFO7A9Kgtv6hdCUcXXgisL9w0DB5lF_UKFKIQyj_7iih44F4Lc_DEvTOLyL-ZO2g")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block();

        ProductResponseDto productInfo = webClient
                .get()
                .uri("http://127.0.0.1:8081/api/v1/catalog/product/get-product?productName={productName}", order.getProductName())
                .header("Authorization","Bearer eyJraWQiOiJkMWY5MDU4My1kZTYwLTQ5ZDEtYjQ1Yy00ZTQyNDQzMGMyYTYiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImdhdGV3YXkiLCJuYmYiOjE2NzAxNzI3MjMsInNjb3BlIjpbImF1dGgudXNlciIsIm9wZW5pZCJdLCJpc3MiOiJodHRwOlwvXC9sb2NhbGhvc3Q6ODA4MiIsImV4cCI6MTY3MDE3MzMyMywiaWF0IjoxNjcwMTcyNzIzfQ.Bgx0jvSksVY2pqGB2zlCSI-Yyo6-rsBDwwf6LpL7DCSt7FMaYEbvvOtxjsQmogk5so0a282ctNcbXRcFPNS6k0udAlDARqS2pxR2YgHUeOtr0tJvt1FbQbB2T7bi_qthgcS7P6tXKGSmtyPhNfeKIhrp8EqPNeDQm9eKQxS3H_0-RmWZOUqSvSP6FXqJ7OccSUiiIFgAXLPaWkDclQaXekp5i4csvp-HGH2ven0EhFItk2W8YLYGX0fiD-cM8LrGhCd8qwUW2MwpIzFYpOZVI6bFO7A9Kgtv6hdCUcXXgisL9w0DB5lF_UKFKIQyj_7iih44F4Lc_DEvTOLyL-ZO2g")
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
