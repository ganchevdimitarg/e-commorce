package com.ganchevdimitarg.order.controller;

import com.ganchevdimitarg.order.dto.OrderCreatedResponse;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
import com.ganchevdimitarg.order.service.IdempotencyService;
import com.ganchevdimitarg.order.service.MailService;
import com.ganchevdimitarg.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private MailService mailService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Authentication authentication;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, mailService, idempotencyService);
    }

    @Test
    void should_delegateThroughIdempotencyService_when_creatingOrder() {
        when(authentication.getName()).thenReturn("john");
        OrderDto orderDto = OrderDto.builder().username("john").build();
        OrderCreatedResponse created = new OrderCreatedResponse(1001L);
        when(orderService.createOrder(orderDto, "john")).thenReturn(created);
        // Run the guarded action so the controller's delegation is exercised end-to-end.
        when(idempotencyService.execute(eq("key-123"), eq(OrderCreatedResponse.class), any()))
                .thenAnswer(inv -> inv.getArgument(2, Supplier.class).get());

        OrderCreatedResponse response = controller.createOrder(orderDto, "key-123", authentication);

        assertThat(response).isEqualTo(created);
        verify(orderService).createOrder(orderDto, "john");
        verify(mailService).sendUserOrderMail("john");
    }

    @Test
    void should_capPageSizeAt100_when_requestedSizeExceedsMax() {
        when(authentication.getName()).thenReturn("john");
        PageResponse<OrderSummaryResponse> emptyResponse = new PageResponse<>(
                List.of(), 0, 100, 0, 0);
        when(orderService.listMyOrders(eq("john"), isNull(), any(Pageable.class)))
                .thenReturn(emptyResponse);

        // Call with page size 500 (exceeds 100 limit)
        Pageable requestedPageable = PageRequest.of(0, 500);
        controller.listMyOrders(null, requestedPageable, authentication);

        // Capture the actual Pageable passed to the service
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listMyOrders(eq("john"), isNull(), pageableCaptor.capture());

        // Assert that size was clamped to 100
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageSize()).isEqualTo(100);
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
    }

    @Test
    void should_passThroughPageable_when_sizeWithinLimit() {
        when(authentication.getName()).thenReturn("john");
        PageResponse<OrderSummaryResponse> emptyResponse = new PageResponse<>(
                List.of(), 1, 20, 0, 0);
        when(orderService.listMyOrders(eq("john"), isNull(), any(Pageable.class)))
                .thenReturn(emptyResponse);

        // Call with page size 20 (within 100 limit)
        Pageable requestedPageable = PageRequest.of(1, 20);
        controller.listMyOrders(null, requestedPageable, authentication);

        // Capture the actual Pageable passed to the service
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listMyOrders(eq("john"), isNull(), pageableCaptor.capture());

        // Assert that size was NOT modified
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageSize()).isEqualTo(20);
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
    }
}
