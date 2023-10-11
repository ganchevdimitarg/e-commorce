package com.concordeu.payment.service.impl;

import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
import com.concordeu.payment.repositories.CustomerRepository;
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
    private final CustomerRepository customerRepository;
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public String createCustomer(UserRequestDto paymentDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("email", paymentDto.username());
        params.put("name", paymentDto.username());

        try {
            Customer customer = Customer.create(params);
            log.info("Method createCustomer: Create customer successful: {}", customer.getEmail());

            customerRepository.save(AppCustomer.builder()
                    .customerId(customer.getId())
                    .username(customer.getEmail())
                    .customerName(customer.getName())
                    .build());
            log.info("Created customer in payment service db with id: " + customer.getId());
            return customer.getId();
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
    public PaymentDto getCustomerByUsername(String username) {
        AppCustomer appCustomer = customerRepository.findByUsername(username).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", username);
            throw new InvalidPaymentRequestException("Customer with username " + username + " does not exist");
        });
        return PaymentDto.builder()
                .username(appCustomer.getUsername())
                .customerName(appCustomer.getCustomerName())
                .customerId(appCustomer.getCustomerId())
                .build();

    }

    /**
     * Permanently deletes a customer.
     * It cannot be undone.
     * Also, immediately cancels any active subscriptions on the customer.
     *
     * @param username customer username
     */
    @Override
    public String deleteCustomer(String username) {
        Stripe.apiKey = secretKey;

        try {
            Customer customerByEmail = getCustomerByStripeId(getCustomerByUsername(username).customerId());
            Customer.retrieve(customerByEmail.getId()).delete();
            AppCustomer customer = customerRepository.findByUsername(username).orElseThrow(() -> {
                log.warn("Customer with username {} does not exist in db customers", username);
                return new InvalidPaymentRequestException("Customer with username " + username + " does not exist");
            });
            customerRepository.delete(customer);
            log.info("Delete customer successful: {}", customerByEmail.getEmail());
            return customerByEmail.getId();
        } catch (StripeException e) {
            log.warn(e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public AppCustomer findByUsername(String username) {
        return customerRepository.findByUsername(username).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", username);
            return new InvalidPaymentRequestException("Customer with username " + username + " does not exist");
        });
    }

    @Override
    public Customer getCustomerByStripeId(String customerId) {
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
