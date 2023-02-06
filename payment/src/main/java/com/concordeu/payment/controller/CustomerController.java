package com.concordeu.payment.controller;

import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.service.CustomerService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payment/customer")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {
    private final CustomerService customerService;

    @PostMapping("/create-customer")
    public PaymentDto createCustomer(@RequestBody PaymentDto paymentDto) throws StripeException {
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
