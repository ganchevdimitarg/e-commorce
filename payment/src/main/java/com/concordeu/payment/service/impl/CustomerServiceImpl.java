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

/**
 * Customers
 * This object represents a customer of your business.
 * It lets you create recurring charges and track payments that belong to the same customer.
 * source: <a href="https://stripe.com/docs/api/customers">...</a>
 */
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
        params.put("email", customerDto.username());
        params.put("name", customerDto.customerName());

        try {
            Customer customer = Customer.create(params);
            log.info("Created customer in STRIPE payment service");

            customerDao.save(AppCustomer.builder()
                    .customerId(customer.getId())
                    .username(customer.getEmail())
                    .customerName(customer.getName())
                    .build());
            log.info("Created customer in payment service db");

        } catch (StripeException e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    /**
     * Retrieves a Customer object.
     *
     * @param username customer username
     * @return Returns the Customer object for a valid identifier.
     * If it’s for a deleted Customer, a subset of the customer’s information is returned,
     * including a deleted property that’s set to true.
     */
    @Override
    public Customer getCustomerByUsername(String username) {
        AppCustomer appCustomer = customerDao.findByUsername(username);
        return getCustomer(appCustomer.getCustomerId());
    }

    /**
     * Permanently deletes a customer.
     * It cannot be undone.
     * Also immediately cancels any active subscriptions on the customer.
     *
     * @param username customer username
     */
    @Override
    public void deleteCustomer(String username) {
        Stripe.apiKey = secretKey;

        try {
            Customer customerByEmail = getCustomerByUsername(username);
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