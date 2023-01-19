package com.concordeu.payment.service.impl;

import com.concordeu.payment.dao.CustomerDao;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
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
 * cardId: <a href="https://stripe.com/docs/api/customers">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerDao customerDao;
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public PaymentDto createCustomer(PaymentDto paymentDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("email", paymentDto.username());
        params.put("name", paymentDto.username());

        try {
            Customer customer = Customer.create(params);
            log.info("Method createCustomer: Create customer successful: {}", customer.getEmail());

            customerDao.save(AppCustomer.builder()
                    .customerId(customer.getId())
                    .username(customer.getEmail())
                    .customerName(customer.getName())
                    .build());
            log.info("Created customer in payment service db");

            return PaymentDto.builder()
                    .username(customer.getEmail())
                    .customerId(customer.getId())
                    .customerName(customer.getName())
                    .build();

        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
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
     * Also, immediately cancels any active subscriptions on the customer.
     *
     * @param username customer username
     */
    @Override
    public void deleteCustomer(String username) {
        Stripe.apiKey = secretKey;

        try {
            Customer customerByEmail = getCustomerByUsername(username);
            Customer.retrieve(customerByEmail.getId()).delete();
            customerDao.delete(customerDao.findByUsername(username));
            log.info("Delete customer successful: {}", customerByEmail.getEmail());
        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
        }
    }

    private Customer getCustomer(String customerId) {
        Stripe.apiKey = secretKey;
        try {
            Customer customer = Customer.retrieve(customerId);
            log.info("Get customer successful: {}", customer.getEmail());
            return customer;
        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
        }
    }
}
