package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.CreateCustomerCommand;
import com.ganchevdimitarg.payment.dto.CustomerResponse;

public interface CustomerService {
    CustomerResponse createCustomer(CreateCustomerCommand command, String idempotencyKey);
    CustomerResponse getCustomerByUsername(String username);
    String deleteCustomer(String username);
}
