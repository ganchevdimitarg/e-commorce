package com.concordeu.payment.service;

import com.concordeu.payment.dto.CustomerDto;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;

public interface CustomerService {
    CustomerDto createCustomer(CustomerDto customerDto) throws StripeException;
    Customer getCustomerByUsername(String username);
    void deleteCustomer(String username);
}
