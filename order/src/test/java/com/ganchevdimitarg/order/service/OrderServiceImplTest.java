package com.ganchevdimitarg.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.order.dao.OrderDao;
import com.ganchevdimitarg.order.dao.OrderStatusHistoryDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.domain.OrderStatusHistory;
import com.ganchevdimitarg.order.dto.OrderCreatedResponse;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderLineDto;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.dto.ProductResponseDto;
import com.ganchevdimitarg.order.dto.UserDto;
import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import com.ganchevdimitarg.order.exception.NotFoundException;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private ChargeService chargeService;
    @Mock
    private OrderStatusHistoryDao statusHistoryDao;
    @Mock
    private OrderPersistence orderPersistence;

    private OrderServiceImpl orderService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        orderService = new OrderServiceImpl(orderDao, builder.build(), circuitBreakerFactory,
                chargeService, statusHistoryDao, orderPersistence);
        ReflectionTestUtils.setField(orderService,
                "catalogServiceGetProductsByIdsUri", "http://catalog/products");
        ReflectionTestUtils.setField(orderService,
                "profileServiceGetProfileByUsernameUri", "http://profile/get?username=");
    }

    /** Circuit breaker that simply runs the supplied call (dependency healthy). */
    private void runSupplier() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Supplier<?> toRun = inv.getArgument(0);
            return toRun.get();
        });
    }

    /** Circuit breaker that trips straight to the fallback (dependency unavailable). */
    private void runFallback() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Function<Throwable, ?> fallback = inv.getArgument(1);
            return fallback.apply(new RuntimeException("dependency down"));
        });
    }

    private void stubProfile(UserDto profile) throws Exception {
        server.expect(requestTo("http://profile/get?username=john")).andExpect(method(GET))
                .andRespond(withSuccess(json.writeValueAsString(profile), MediaType.APPLICATION_JSON));
    }

    private void stubCatalog(Object body) throws Exception {
        server.expect(requestTo("http://catalog/products")).andExpect(method(POST))
                .andRespond(withSuccess(json.writeValueAsString(body), MediaType.APPLICATION_JSON));
    }

    private static OrderDto orderFor(String user, OrderLineDto... lines) {
        return OrderDto.builder().username(user).deliveryComment("leave at door")
                .items(List.of(lines)).build();
    }

    // --- create ---------------------------------------------------------------------------

    @Test
    void should_throwNotFound_when_creatingOrderForAnotherUser() {
        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "mallory"))
                .isInstanceOf(NotFoundException.class);
        verify(orderPersistence, never()).placeOrder(any(), anyString());
    }

    @Test
    void should_throwConflict_when_noProfileRegistered() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("").build());

        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john"))
                .isInstanceOf(ConflictException.class);
        verify(orderPersistence, never()).placeOrder(any(), anyString());
    }

    @Test
    void should_throwServiceUnavailable_when_profileDependencyDown() {
        runFallback();

        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john"))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(orderPersistence, never()).placeOrder(any(), anyString());
    }

    @Test
    void should_throwInvalidRequest_when_catalogReturnsNoProducts() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("john").cardId("card_1").build());
        stubCatalog(List.of());

        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john"))
                .isInstanceOf(InvalidRequestDataException.class);
        verify(orderPersistence, never()).placeOrder(any(), anyString());
    }

    @Test
    void should_placeChargeAndConfirm_when_createOrderSucceeds() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("john").cardId("card_1").build());
        stubCatalog(List.of(ProductResponseDto.builder().id("p_1").name("Widget")
                .price(new BigDecimal("10.00")).build()));
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();
        when(orderPersistence.placeOrder(any(), eq("john")))
                .thenReturn(Order.builder().orderNumber(1).username("john").build());
        when(chargeService.makePayment(eq("card_1"), eq("john"), eq(1000L))).thenReturn(payment);

        OrderCreatedResponse response =
                orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john");

        assertThat(response.orderNumber()).isEqualTo(1L);
        verify(orderPersistence).placeOrder(any(), eq("john"));
        verify(chargeService).makePayment("card_1", "john", 1000L);
        verify(orderPersistence).confirmPaid(1L, payment, "john");
        server.verify();
    }

    @Test
    void should_chargePricePerQuantityInCents_when_multipleUnits() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("john").cardId("card_1").build());
        stubCatalog(List.of(ProductResponseDto.builder().id("p_1").name("Widget")
                .price(new BigDecimal("10.50")).build()));
        when(orderPersistence.placeOrder(any(), eq("john")))
                .thenReturn(Order.builder().orderNumber(1).username("john").build());
        when(chargeService.makePayment(anyString(), anyString(), anyLong()))
                .thenReturn(PaymentDto.builder().chargeId("ch_1").build());

        orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 3)), "john");

        // 10.50 * 3 = 31.50 -> 3150 cents
        verify(chargeService).makePayment("card_1", "john", 3150L);
    }

    @Test
    void should_markPaymentFailed_when_chargeThrows() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("john").cardId("card_1").build());
        stubCatalog(List.of(ProductResponseDto.builder().id("p_1").name("Widget")
                .price(new BigDecimal("10.00")).build()));
        when(orderPersistence.placeOrder(any(), eq("john")))
                .thenReturn(Order.builder().orderNumber(7).username("john").build());
        when(chargeService.makePayment(anyString(), anyString(), anyLong()))
                .thenThrow(new InvalidRequestDataException("payment down"));

        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john"))
                .isInstanceOf(InvalidRequestDataException.class);

        verify(orderPersistence).markPaymentFailed(eq(7L), eq("john"), anyString());
        verify(orderPersistence, never()).confirmPaid(anyLong(), any(), anyString());
        verify(chargeService, never()).refund(anyString(), anyLong(), anyString());
    }

    @Test
    void should_refundAndMarkFailed_when_confirmFailsAfterCharge() throws Exception {
        runSupplier();
        stubProfile(UserDto.builder().username("john").cardId("card_1").build());
        stubCatalog(List.of(ProductResponseDto.builder().id("p_1").name("Widget")
                .price(new BigDecimal("10.00")).build()));
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();
        when(orderPersistence.placeOrder(any(), eq("john")))
                .thenReturn(Order.builder().orderNumber(8).username("john").build());
        when(chargeService.makePayment(anyString(), anyString(), anyLong())).thenReturn(payment);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(orderPersistence).confirmPaid(eq(8L), any(), eq("john"));

        assertThatThrownBy(() -> orderService.createOrder(orderFor("john", new OrderLineDto("p_1", 1)), "john"))
                .isInstanceOf(IllegalStateException.class);

        verify(chargeService).refund("ch_1", 0L, "john");
        verify(orderPersistence).markPaymentFailed(eq(8L), eq("john"), anyString());
    }

    // --- delete / read --------------------------------------------------------------------

    @Test
    void should_softDeleteViaEntity_when_orderExists() {
        Order order = Order.builder().orderNumber(7).username("john").build();
        when(orderDao.findByOrderNumber(7)).thenReturn(Optional.of(order));

        orderService.deleteOrder(7, "john");

        verify(orderDao).delete(order);
    }

    @Test
    void should_throwNotFound_when_orderMissingOnDelete() {
        when(orderDao.findByOrderNumber(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(99, "john"))
                .isInstanceOf(NotFoundException.class);
        verify(orderDao, never()).delete(any());
    }

    @Test
    void should_throwNotFound_when_deleteRequestedByAnotherUser() {
        Order order = Order.builder().orderNumber(7).username("alice").build();
        when(orderDao.findByOrderNumber(7)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deleteOrder(7, "mallory"))
                .isInstanceOf(NotFoundException.class);
        verify(orderDao, never()).delete(any());
    }

    @Test
    void should_throwNotFound_when_orderNotFoundOnGet() {
        when(orderDao.findByOrderNumber(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(5, "john"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void should_throwNotFound_when_getOrderRequestedByAnotherUser() {
        Order order = Order.builder().orderNumber(5).username("john").build();
        when(orderDao.findByOrderNumber(5)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(5, "mallory"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_returnPagedSummaries_when_listingMyOrders() {
        Order order = Order.builder().orderNumber(1).username("john")
                .status(OrderStatus.PAID).deliveryComment("door")
                .createdOn(java.time.LocalDateTime.now()).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(orderDao.findByUsername("john", pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        var result = orderService.listMyOrders("john", null, pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(s -> {
            assertThat(s.orderNumber()).isEqualTo(1);
            assertThat(s.status()).isEqualTo(OrderStatus.PAID);
        });
    }

    @Test
    void should_filterByStatus_when_statusProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderDao.findByUsernameAndStatus("john", OrderStatus.CANCELLED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = orderService.listMyOrders("john", OrderStatus.CANCELLED, pageable);

        assertThat(result.totalElements()).isZero();
        verify(orderDao).findByUsernameAndStatus("john", OrderStatus.CANCELLED, pageable);
    }

    @Test
    void should_returnTrackingTimeline_when_ownerRequests() {
        Order order = Order.builder().orderNumber(3).username("john")
                .status(OrderStatus.SHIPPED).build();
        when(orderDao.findByOrderNumber(3)).thenReturn(Optional.of(order));
        OrderStatusHistory h1 = OrderStatusHistory.builder().order(order).fromStatus(null)
                .toStatus(OrderStatus.PLACED).changedBy("john").reason("order placed").build();
        when(statusHistoryDao.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of(h1));

        var result = orderService.getTracking(3, "john");

        assertThat(result.orderNumber()).isEqualTo(3);
        assertThat(result.currentStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.history()).singleElement()
                .satisfies(e -> assertThat(e.toStatus()).isEqualTo(OrderStatus.PLACED));
    }

    @Test
    void should_throwNotFound_when_trackingRequestedByNonOwner() {
        Order order = Order.builder().orderNumber(3).username("john").build();
        when(orderDao.findByOrderNumber(3)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getTracking(3, "mallory"))
                .isInstanceOf(NotFoundException.class);
    }

    // --- cancel / advance -----------------------------------------------------------------

    @Test
    void should_refundAndCancel_when_cancellingPaidOrder() {
        Charge charge = Charge.builder().chargeId("ch_stp_1").status("succeeded").build();
        Order order = Order.builder().orderNumber(9).username("john")
                .status(OrderStatus.PAID).charge(charge).build();
        when(orderDao.findByOrderNumber(9)).thenReturn(Optional.of(order));

        orderService.cancelOrder(9, "john", "changed my mind");

        verify(chargeService).refund("ch_stp_1", 0L, "john");
        verify(orderPersistence).transition(order, OrderStatus.CANCELLED, "john", "changed my mind");
    }

    @Test
    void should_cancelWithoutRefund_when_orderNotYetPaid() {
        Order order = Order.builder().orderNumber(10).username("john")
                .status(OrderStatus.PLACED).build();
        when(orderDao.findByOrderNumber(10)).thenReturn(Optional.of(order));

        orderService.cancelOrder(10, "john", null);

        verify(chargeService, never()).refund(anyString(), anyLong(), anyString());
        verify(orderPersistence).transition(order, OrderStatus.CANCELLED, "john", null);
    }

    @Test
    void should_rejectCancel_when_orderAlreadyShipped() {
        Order order = Order.builder().orderNumber(11).username("john")
                .status(OrderStatus.SHIPPED).build();
        when(orderDao.findByOrderNumber(11)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(11, "john", null))
                .isInstanceOf(ConflictException.class);
        verify(chargeService, never()).refund(anyString(), anyLong(), anyString());
        verify(orderPersistence, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void should_advanceStatus_when_transitionLegal() {
        Order order = Order.builder().orderNumber(20).username("john")
                .status(OrderStatus.PAID).build();
        when(orderDao.findByOrderNumber(20)).thenReturn(Optional.of(order));

        orderService.advanceStatus(20, OrderStatus.SHIPPED, "ops", "left warehouse");

        verify(orderPersistence).transition(order, OrderStatus.SHIPPED, "ops", "left warehouse");
    }

    @Test
    void should_rejectAdvanceToCancelled_when_usingAdminAdvance() {
        Order order = Order.builder().orderNumber(22).username("john")
                .status(OrderStatus.PAID).build();
        when(orderDao.findByOrderNumber(22)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.advanceStatus(22, OrderStatus.CANCELLED, "ops", null))
                .isInstanceOf(ConflictException.class);
        verify(orderPersistence, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void should_rejectAdvanceToPaymentFailed_when_usingAdminAdvance() {
        Order order = Order.builder().orderNumber(23).username("john")
                .status(OrderStatus.PLACED).build();
        when(orderDao.findByOrderNumber(23)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.advanceStatus(23, OrderStatus.PAYMENT_FAILED, "ops", null))
                .isInstanceOf(ConflictException.class);
        verify(orderPersistence, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void should_throwNotFound_when_advancingMissingOrder() {
        when(orderDao.findByOrderNumber(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.advanceStatus(99, OrderStatus.SHIPPED, "ops", null))
                .isInstanceOf(NotFoundException.class);
    }
}
