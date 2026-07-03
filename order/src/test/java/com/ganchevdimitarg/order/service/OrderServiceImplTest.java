package com.ganchevdimitarg.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.order.dao.ItemDao;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.domain.Item;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.dto.ProductResponseDto;
import com.ganchevdimitarg.order.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock
    private OrderDao orderDao;
    @Mock
    private ItemDao itemDao;
    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private ChargeService chargeService;
    @Mock
    private com.ganchevdimitarg.order.dao.OrderStatusHistoryDao statusHistoryDao;

    private OrderServiceImpl orderService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        orderService = new OrderServiceImpl(orderDao, itemDao, builder.build(),
                circuitBreakerFactory, chargeService, statusHistoryDao);
        ReflectionTestUtils.setField(orderService,
                "catalogServiceGetProductsByIdsUri", "http://catalog/products");
        ReflectionTestUtils.setField(orderService,
                "profileServiceGetProfileByUsernameUri", "http://profile/get?username=");
        ReflectionTestUtils.setField(orderService,
                "profileServiceCreateUserUri", "http://profile/register");
    }

    private void runSupplier() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Supplier<?> toRun = inv.getArgument(0);
            return toRun.get();
        });
    }

    @Test
    void should_softDeleteViaEntity_when_orderExists() {
        Order order = Order.builder().orderNumber(7).username("john").build();
        when(orderDao.findByOrderNumber(7)).thenReturn(Optional.of(order));

        orderService.deleteOrder(7);

        verify(orderDao).delete(order);
    }

    @Test
    void should_notDelete_when_orderMissing() {
        when(orderDao.findByOrderNumber(99)).thenReturn(Optional.empty());

        orderService.deleteOrder(99);

        verify(orderDao, never()).delete(any());
    }

    @Test
    void should_throw_when_orderNotFoundOnGet() {
        when(orderDao.findByOrderNumber(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(5, "john"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No such order");
    }

    @Test
    void should_throw_when_getOrderRequestedByAnotherUser() {
        Order order = Order.builder().orderNumber(5).username("john").build();
        when(orderDao.findByOrderNumber(5)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(5, "mallory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You cannot access this information!");
    }

    @Test
    void should_throw_when_createOrderForAnotherUser() {
        OrderDto dto = OrderDto.builder().username("john").items(List.of()).build();

        assertThatThrownBy(() -> orderService.createOrder(dto, "mallory"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_persistOrderAndCharge_when_createOrderSucceeds() throws Exception {
        runSupplier();
        UserDto profile = UserDto.builder().username("john").cardId("card_1").build();
        ProductResponseDto product = ProductResponseDto.builder()
                .name("Widget").price(new BigDecimal("10.00")).build();
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        server.expect(requestTo("http://profile/get?username=john"))
                .andExpect(method(GET))
                .andRespond(withSuccess(json.writeValueAsString(profile), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://catalog/products"))
                .andExpect(method(POST))
                .andRespond(withSuccess(json.writeValueAsString(List.of(product)), MediaType.APPLICATION_JSON));

        when(chargeService.makePayment(anyString(), anyString(), anyLong())).thenReturn(payment);
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Item item = Item.builder().productId("p_1").quantity(1).build();
        OrderDto dto = OrderDto.builder()
                .username("john")
                .deliveryComment("leave at door")
                .items(List.of(item))
                .build();

        orderService.createOrder(dto, "john");

        verify(orderDao, org.mockito.Mockito.atLeastOnce()).saveAndFlush(any(Order.class));
        verify(itemDao).saveAllAndFlush(any());
        verify(chargeService).saveCharge(any(Order.class), any(PaymentDto.class));
        server.verify();
    }

    @Test
    void should_setStatusPaidAndRecordHistory_when_createOrderSucceeds() throws Exception {
        runSupplier();
        UserDto profile = UserDto.builder().username("john").cardId("card_1").build();
        ProductResponseDto product = ProductResponseDto.builder()
                .name("Widget").price(new java.math.BigDecimal("10.00")).build();
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        server.expect(requestTo("http://profile/get?username=john"))
                .andExpect(method(GET))
                .andRespond(withSuccess(json.writeValueAsString(profile), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://catalog/products"))
                .andExpect(method(POST))
                .andRespond(withSuccess(json.writeValueAsString(List.of(product)), MediaType.APPLICATION_JSON));

        when(chargeService.makePayment(anyString(), anyString(), anyLong())).thenReturn(payment);
        when(orderDao.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Item item = Item.builder().productId("p_1").quantity(1).build();
        OrderDto dto = OrderDto.builder()
                .username("john").deliveryComment("leave at door").items(List.of(item)).build();

        orderService.createOrder(dto, "john");

        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderDao, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(com.ganchevdimitarg.order.domain.OrderStatus.PAID);
        // one history row for null->PLACED, one for PLACED->PAID
        verify(statusHistoryDao, org.mockito.Mockito.times(2))
                .save(any(com.ganchevdimitarg.order.domain.OrderStatusHistory.class));
    }

    @Test
    void should_returnPagedSummaries_when_listingMyOrders() {
        Order order = Order.builder()
                .orderNumber(1).username("john")
                .status(com.ganchevdimitarg.order.domain.OrderStatus.PAID)
                .deliveryComment("door").createdOn(java.time.LocalDateTime.now())
                .build();
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(orderDao.findByUsername("john", pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));

        var result = orderService.listMyOrders("john", null, pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .satisfies(s -> {
                    assertThat(s.orderNumber()).isEqualTo(1);
                    assertThat(s.status()).isEqualTo(com.ganchevdimitarg.order.domain.OrderStatus.PAID);
                });
    }

    @Test
    void should_filterByStatus_when_statusProvided() {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(orderDao.findByUsernameAndStatus("john",
                com.ganchevdimitarg.order.domain.OrderStatus.CANCELLED, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

        var result = orderService.listMyOrders("john",
                com.ganchevdimitarg.order.domain.OrderStatus.CANCELLED, pageable);

        assertThat(result.totalElements()).isZero();
        verify(orderDao).findByUsernameAndStatus("john",
                com.ganchevdimitarg.order.domain.OrderStatus.CANCELLED, pageable);
    }
}
