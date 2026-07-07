package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CreateCustomerCommand;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private CustomerPersistence customerPersistence;
    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    void should_createCustomerWithIdempotencyKeyAndPersist_when_createCustomer() {
        CreateCustomerCommand command = new CreateCustomerCommand("john@doe.com");
        GatewayCustomer gatewayCustomer = new GatewayCustomer("cus_1", "john@doe.com", "john@doe.com");
        when(paymentGateway.createCustomer("john@doe.com", "john@doe.com", "idem-123"))
                .thenReturn(gatewayCustomer);
        CustomerResponse persisted = new CustomerResponse("cus_1", "john@doe.com", "john@doe.com");
        when(customerPersistence.persistCustomer(gatewayCustomer)).thenReturn(persisted);

        CustomerResponse response = customerService.createCustomer(command, "idem-123");

        assertThat(response).isEqualTo(persisted);
        verify(paymentGateway).createCustomer("john@doe.com", "john@doe.com", "idem-123");
        verify(customerPersistence).persistCustomer(gatewayCustomer);
    }

    @Test
    void should_returnCustomerView_when_customerExists() {
        AppCustomer customer = AppCustomer.builder()
                .customerId("cus_1").username("john@doe.com").customerName("John").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerByUsername("john@doe.com");

        assertThat(response).isEqualTo(new CustomerResponse("cus_1", "john@doe.com", "John"));
    }

    @Test
    void should_throwNotFound_when_customerMissing() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerByUsername("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_softDeleteAndCallGateway_when_deleteCustomer() {
        AppCustomer customer = AppCustomer.builder()
                .customerId("cus_1").username("john@doe.com").customerName("John").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));

        String deletedId = customerService.deleteCustomer("john@doe.com");

        assertThat(deletedId).isEqualTo("cus_1");
        assertThat(customer.getDeletedAt()).isNotNull();
        verify(paymentGateway).deleteCustomer("cus_1");
        verify(customerDao).save(customer);
    }

    @Test
    void should_throwNotFound_when_deletingMissingCustomer() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.deleteCustomer("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
