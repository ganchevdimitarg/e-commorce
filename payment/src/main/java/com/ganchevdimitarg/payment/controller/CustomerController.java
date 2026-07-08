package com.ganchevdimitarg.payment.controller;

import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment/customer")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {
    private final CustomerService customerService;

    @PostMapping("/create-customer")
    public CustomerResponse createCustomer(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return customerService.createCustomer(userId, idempotencyKey);
    }

    @GetMapping("/get-customer")
    public CustomerResponse getCustomer(@RequestHeader("X-User-Id") String userId) {
        return customerService.getCurrentCustomer(userId);
    }

    @DeleteMapping("/delete-customer")
    public String deleteCustomer(@RequestHeader("X-User-Id") String userId) {
        return customerService.deleteCustomer(userId);
    }
}
