package com.concordeu.payment.service;

import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.payment.domain.AppCustomer;
import com.stripe.model.Customer;

public interface CustomerService {
    String createCustomer(UserRequestDto customerDto);
    PaymentDto getCustomerByUsername(String username);
    String deleteCustomer(String username);
    AppCustomer findByUsername(String username);
}
