package com.concordeu.order.controller;

import com.concordeu.order.annotation.ValidationRequest;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.service.MailService;
import com.concordeu.order.service.OrderService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderService orderService;
    private final MailService mailService;
    private final WebClient webClient;

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
    @Observed(
            name = "user.name",
            contextualName = "createOrder",
            lowCardinalityKeyValues = {"method", "createOrder"}
    )
    public Mono<OrderDto> createOrder(@RequestBody OrderDto orderDto, Authentication authentication) {
        Mono<OrderDto> order = orderService.createOrder(orderDto, authentication.getName());
        mailService.sendUserOrderMail(orderDto.username());
        return order;
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
    @Observed(
            name = "user.name",
            contextualName = "deleteOrder",
            lowCardinalityKeyValues = {"method", "deleteOrder"}
    )
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
    @Observed(
            name = "user.name",
            contextualName = "getOrder",
            lowCardinalityKeyValues = {"method", "getOrder"}
    )
    public Mono<OrderResponseDto> getOrder(@RequestParam Long orderId, Authentication authentication) {
        return orderService.getOrder(orderId, authentication.getName());
    }

    @GetMapping("/test")
    @Observed(
            name = "user.name",
            contextualName = "test",
            lowCardinalityKeyValues = {"method", "test"}
    )
    public Mono<String> test() {
        return this.webClient
                .get()
                .uri("http://localhost:8083/api/v1/profile/test")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
    }
}
