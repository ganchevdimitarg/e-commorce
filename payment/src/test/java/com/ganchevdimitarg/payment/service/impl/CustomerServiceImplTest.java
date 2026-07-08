package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    private static final String USER = "john@doe.com";

    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private CustomerPersistence customerPersistence;
    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    void should_createCustomerForAuthenticatedUserAndPersist_when_createCustomer() {
        GatewayCustomer gatewayCustomer = new GatewayCustomer("cus_1", USER, USER);
        when(paymentGateway.createCustomer(USER, USER, "idem-123")).thenReturn(gatewayCustomer);
        CustomerResponse persisted = new CustomerResponse("cus_1", USER, USER);
        when(customerPersistence.persistCustomer(gatewayCustomer)).thenReturn(persisted);

        CustomerResponse response = customerService.createCustomer(USER, "idem-123");

        assertThat(response).isEqualTo(persisted);
        verify(paymentGateway).createCustomer(USER, USER, "idem-123");
        verify(customerPersistence).persistCustomer(gatewayCustomer);
    }

    @Test
    void should_returnCustomerView_when_customerExists() {
        AppCustomer customer = AppCustomer.builder()
                .customerId("cus_1").username(USER).customerName("John").build();
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCurrentCustomer(USER);

        assertThat(response).isEqualTo(new CustomerResponse("cus_1", USER, "John"));
    }

    @Test
    void should_throwNotFound_when_customerMissing() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCurrentCustomer("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_deleteAtProviderThenDelegateSoftDelete_when_deleteCustomer() {
        AppCustomer customer = AppCustomer.builder()
                .customerId("cus_1").username(USER).customerName("John").build();
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer));

        String deletedId = customerService.deleteCustomer(USER);

        assertThat(deletedId).isEqualTo("cus_1");
        // Provider delete happens before the local soft-delete persistence, and the
        // soft-delete is delegated to the transactional collaborator (not an in-bean write).
        InOrder inOrder = inOrder(paymentGateway, customerPersistence);
        inOrder.verify(paymentGateway).deleteCustomer("cus_1");
        inOrder.verify(customerPersistence).softDelete(customer);
    }

    @Test
    void should_throwNotFound_when_deletingMissingCustomer() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.deleteCustomer("missing"))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).deleteCustomer("missing");
        verify(customerPersistence, never()).softDelete(any());
    }
}
