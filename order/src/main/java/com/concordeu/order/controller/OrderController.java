package com.concordeu.order.controller;

import com.concordeu.order.annotation.ValidationRequest;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.service.MailService;
import com.concordeu.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Slf4j
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
    public Mono<OrderDto> createOrder(@RequestBody OrderDto orderDto, Authentication authentication) {
        mailService.sendUserOrderMail(orderDto.username());
        return orderService.createOrder(orderDto, authentication.getName());
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
    public Mono<Void> deleteOrder(@RequestParam Long orderId) {
        return orderService.deleteOrder(orderId);
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
    public Mono<OrderResponseDto> getOrder(@RequestParam Long orderId, Authentication authentication) {
        return orderService.getOrder(orderId, authentication.getName());
    }
}
