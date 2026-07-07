package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerPersistenceTest {

    @Mock
    private CustomerDao customerDao;
    @InjectMocks
    private CustomerPersistence customerPersistence;

    @Test
    void should_mapGatewayCustomerToEntityAndReturnResponse_when_persisting() {
        GatewayCustomer gatewayCustomer = new GatewayCustomer("cus_1", "john@doe.com", "John");

        CustomerResponse response = customerPersistence.persistCustomer(gatewayCustomer);

        ArgumentCaptor<AppCustomer> captor = ArgumentCaptor.forClass(AppCustomer.class);
        verify(customerDao).save(captor.capture());
        AppCustomer persisted = captor.getValue();
        assertThat(persisted.getCustomerId()).isEqualTo("cus_1");
        assertThat(persisted.getUsername()).isEqualTo("john@doe.com");
        assertThat(persisted.getCustomerName()).isEqualTo("John");
        assertThat(response).isEqualTo(new CustomerResponse("cus_1", "john@doe.com", "John"));
    }
}
