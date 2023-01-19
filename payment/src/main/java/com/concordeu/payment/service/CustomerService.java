package com.concordeu.payment.service;

import com.concordeu.payment.dto.PaymentDto;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;

public interface CustomerService {
    PaymentDto createCustomer(PaymentDto customerDto) throws StripeException;
    Customer getCustomerByUsername(String username);
    void deleteCustomer(String username);
}
