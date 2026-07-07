package com.ganchevdimitarg.payment.controller;

import com.ganchevdimitarg.payment.dto.CreateCustomerCommand;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.service.CustomerService;
import jakarta.validation.Valid;
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
    public CustomerResponse createCustomer(@RequestBody @Valid CreateCustomerCommand command) {
        return customerService.createCustomer(command);
    }

    @GetMapping("/get-customer")
    public CustomerResponse getCustomer(@RequestParam String username) {
        return customerService.getCustomerByUsername(username);
    }

    @DeleteMapping("/delete-customer")
    public String deleteCustomer(@RequestParam String username) {
        return customerService.deleteCustomer(username);
    }
}
