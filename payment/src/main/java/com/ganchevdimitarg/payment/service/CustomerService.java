package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.CustomerResponse;

public interface CustomerService {
    CustomerResponse createCustomer(String userId, String idempotencyKey);

    CustomerResponse getCurrentCustomer(String userId);

    String deleteCustomer(String userId);
}
