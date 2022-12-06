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
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.http.HttpRequest;

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
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-order")
    @ValidationRequest
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:write', 'SCOPE_user:write')")
    public void createOrder(@RequestBody OrderDto orderDto) {
        orderService.createOrder(orderDto);
        mailService.sendUserOrderMail(orderDto.username());
    }

    @Operation(summary = "Delete Order", description = "Delete order by order number",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-order")
    @PreAuthorize("hasAuthority('SCOPE_admin:write')")
    public void deleteOrder(@RequestParam long orderNumber) {
        orderService.deleteOrder(orderNumber);
    }

    @Operation(summary = "Get Order", description = "Get order by order number",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-order")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:read', 'SCOPE_worker:read', 'SCOPE_user:read')")
    public OrderResponseDto getOrder(@RequestParam long orderNumber, HttpServletRequest request) {
        return orderService.getOrder(orderNumber, request.getHeader("Authorization"));
    }
}
