package com.concordeu.payment.controller;

import com.concordeu.payment.dto.CustomerDto;
import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.service.CustomerService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
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
    public PaymentDto createCustomer(@RequestBody PaymentDto paymentDto) throws StripeException {
        CustomerDto customer = customerService.createCustomer(paymentDto);
        return PaymentDto.builder()
                .username(customer.username())
                .customerId(customer.customerId())
                .customerName(customer.customerName())
                .build();
    }

    @GetMapping("/get-customer")
    public CustomerDto getCustomer(@RequestParam String username) {
        Customer customer = customerService.getCustomerByUsername(username);
        return CustomerDto.builder()
                .username(customer.getEmail())
                .customerName(customer.getName())
                .customerId(customer.getId())
                .build();
    }

    @DeleteMapping("/delete-customer")
    public void deleteCustomer(@RequestParam String username) {
        customerService.deleteCustomer(username);
    }
}
