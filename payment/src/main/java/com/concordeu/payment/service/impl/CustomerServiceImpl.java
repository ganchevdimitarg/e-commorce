package com.concordeu.payment.service.impl;

import com.concordeu.payment.dao.CustomerDao;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.payment.dto.CustomerDto;
import com.concordeu.payment.service.CustomerService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerDao customerDao;
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public void createCustomer(CustomerDto customerDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("email", customerDto.email());
        params.put("name", customerDto.customerName());
        params.put("payment_method", customerDto.paymentMethod());

        try {
            Customer customer = Customer.create(params);
            log.info("Created customer in STRIPE payment service");

            customerDao.save(AppCustomer.builder()
                    .customerId(customer.getId())
                    .email(customer.getEmail())
                    .customerName(customer.getName())
                    .build());
            log.info("Created customer in payment service db");

        } catch (StripeException e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    @Override
    public Customer getCustomerByEmail(String email) {
        AppCustomer appCustomer = customerDao.findByEmail(email);
        return getCustomer(appCustomer.getCustomerId());
    }

    @Override
    public void deleteCustomer(String email) {
        Stripe.apiKey = secretKey;

        try {
            Customer customerByEmail = getCustomerByEmail(email);
            Customer
                    .retrieve(customerByEmail.getId())
                    .delete();
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    private Customer getCustomer(String customerId) {
        Stripe.apiKey = secretKey;

        try {
            return Customer.retrieve(customerId);
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }
}
