package com.concordeu.payment.controller;

import com.concordeu.payment.dto.CustomerDto;
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
    public CustomerDto createCustomer(@RequestBody CustomerDto customerDto) throws StripeException {
       return customerService.createCustomer(customerDto);
    }

    @GetMapping("/get-customer")
    public CustomerDto getCustomer(@RequestParam String username) {
        Customer customer = customerService.getCustomerByUsername(username);
        return CustomerDto.getCustomer(customer);
    }

    @DeleteMapping("/delete-customer")
    public void deleteCustomer(@RequestParam String username) {
        customerService.deleteCustomer(username);
    }
}
