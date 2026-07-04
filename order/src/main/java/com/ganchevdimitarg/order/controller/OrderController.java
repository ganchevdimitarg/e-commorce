package com.ganchevdimitarg.order.controller;

import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.dto.CancelOrderRequest;
import com.ganchevdimitarg.order.dto.OrderCreatedResponse;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.OrderTrackingResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
import com.ganchevdimitarg.order.dto.UpdateOrderStatusRequest;
import com.ganchevdimitarg.order.service.IdempotencyService;
import com.ganchevdimitarg.order.service.MailService;
import com.ganchevdimitarg.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderController {
    private final OrderService orderService;
    private final MailService mailService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Create Order", description = "Create order in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-order")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse createOrder(
            @RequestBody @jakarta.validation.Valid OrderDto orderDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return idempotencyService.execute(idempotencyKey, OrderCreatedResponse.class, () -> {
            OrderCreatedResponse response = orderService.createOrder(orderDto, authentication.getName());
            mailService.sendUserOrderMail(orderDto.username());
            return response;
        });
    }

    @Operation(summary = "Delete Order", description = "Delete order by order cardNumber",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-order")
    public void deleteOrder(@RequestParam long orderNumber, Authentication authentication) {
        orderService.deleteOrder(orderNumber, authentication.getName());
    }

    @Operation(summary = "Get Order", description = "Get order by order cardNumber",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-order")
    public OrderResponseDto getOrder(@RequestParam long orderNumber, Authentication authentication) {
        return orderService.getOrder(orderNumber, authentication.getName());
    }

    @Operation(summary = "List my orders", description = "Paginated list of the caller's orders",
            security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    public PageResponse<OrderSummaryResponse> listMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        // Pageable has no @Max-annotatable size property, so the 100-item cap is enforced
        // with an explicit clamp rather than Bean Validation.
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }
        return orderService.listMyOrders(authentication.getName(), status, pageable);
    }

    @Operation(summary = "Track order", description = "Current status and full status history",
            security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/{orderNumber}/tracking")
    public OrderTrackingResponse trackOrder(@PathVariable long orderNumber,
                                            Authentication authentication) {
        return orderService.getTracking(orderNumber, authentication.getName());
    }

    @Operation(summary = "Cancel order", description = "Cancel an order and refund if already paid",
            security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/{orderNumber}/cancel")
    public void cancelOrder(@PathVariable long orderNumber,
                            @RequestBody(required = false) CancelOrderRequest request,
                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            Authentication authentication) {
        String reason = request == null ? null : request.reason();
        idempotencyService.execute(idempotencyKey, String.class, () -> {
            orderService.cancelOrder(orderNumber, authentication.getName(), reason);
            return "cancelled";
        });
    }

    @Operation(summary = "Advance order status", description = "Ops-only status transition",
            security = @SecurityRequirement(name = "security_auth"))
    @PatchMapping("/{orderNumber}/status")
    public void advanceStatus(@PathVariable long orderNumber,
                              @RequestBody @jakarta.validation.Valid UpdateOrderStatusRequest request,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                              Authentication authentication) {
        idempotencyService.execute(idempotencyKey, String.class, () -> {
            orderService.advanceStatus(orderNumber, request.status(),
                    authentication.getName(), request.reason());
            return "advanced";
        });
    }
}
