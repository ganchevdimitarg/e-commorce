package com.concordeu.payment.service;

import com.concordeu.payment.dto.PaymentDto;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;

public interface CustomerService {
    PaymentDto createCustomer(PaymentDto customerDto) throws StripeException;
    PaymentDto getCustomerByUsername(String username);
    Customer getCustomerByStripeId(String customerId);
    String deleteCustomer(String username);
}
