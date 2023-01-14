package com.concordeu.payment.service;

import com.concordeu.payment.dto.CustomerDto;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;

public interface CustomerService {
    void createCustomer(CustomerDto customerDto) throws StripeException;
    Customer getCustomerByEmail(String email);
    void deleteCustomer(String email);
}
