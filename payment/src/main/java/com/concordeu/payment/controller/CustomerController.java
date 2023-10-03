package com.concordeu.payment.controller;

import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.service.CustomerService;
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
    public String createCustomer(@RequestBody PaymentDto paymentDto) {
        return customerService.createCustomer(paymentDto);
    }

    @GetMapping("/get-customer")
    public PaymentDto getCustomer(@RequestParam String username) {
        return customerService.getCustomerByUsername(username);
    }

    @DeleteMapping("/delete-customer")
    public String deleteCustomer(@RequestParam String username) {
        return customerService.deleteCustomer(username);
    }
}
