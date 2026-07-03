package com.ganchevdimitarg.order.controller;

import com.ganchevdimitarg.order.annotation.ValidationRequest;
import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Operation(summary = "Create Order", description = "Create order in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-order")
    @ValidationRequest
    @PreAuthorize("hasAuthority('SCOPE_order.write')")
    public void createOrder(@RequestBody OrderDto orderDto, Authentication authentication) {
        orderService.createOrder(orderDto, authentication.getName());
        mailService.sendUserOrderMail(orderDto.username());
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
    @PreAuthorize("hasAuthority('SCOPE_order.write')")
    public void deleteOrder(@RequestParam long orderNumber) {
        orderService.deleteOrder(orderNumber);
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
    @PreAuthorize("hasAuthority('SCOPE_order.read')")
    public OrderResponseDto getOrder(@RequestParam long orderNumber, Authentication authentication) {
        return orderService.getOrder(orderNumber, authentication.getName());
    }

    @Operation(summary = "List my orders", description = "Paginated list of the caller's orders",
            security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_order.read')")
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
}
