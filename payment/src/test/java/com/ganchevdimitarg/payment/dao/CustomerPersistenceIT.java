package com.ganchevdimitarg.payment.dao;

import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CustomerPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private CustomerDao customerDao;

    @Test
    void should_populateAuditColumns_when_customerSaved() {
        AppCustomer saved = customerDao.saveAndFlush(AppCustomer.builder()
                .customerId("cus_1").username("john@doe.com").customerName("John").build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void should_excludeSoftDeleted_when_findByUsername() {
        AppCustomer customer = customerDao.saveAndFlush(AppCustomer.builder()
                .customerId("cus_2").username("jane@doe.com").customerName("Jane").build());
        assertThat(customerDao.findByUsername("jane@doe.com")).isPresent();

        customer.setDeletedAt(Instant.now());
        customerDao.saveAndFlush(customer);

        assertThat(customerDao.findByUsername("jane@doe.com")).isEmpty();
    }
}
