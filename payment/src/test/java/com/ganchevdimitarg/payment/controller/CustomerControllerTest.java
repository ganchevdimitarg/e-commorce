package com.ganchevdimitarg.payment.controller;

import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.payment.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(customerService))
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    void should_return200AndCustomer_when_createFromAuthenticatedUser() throws Exception {
        when(customerService.createCustomer(any(), any()))
                .thenReturn(new CustomerResponse("cus_1", "john@doe.com", "John"));

        mockMvc.perform(post("/api/v1/payment/customer/create-customer")
                        .header("X-User-Id", "john@doe.com")
                        .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cus_1"));

        verify(customerService).createCustomer(eq("john@doe.com"), eq("idem-1"));
    }

    @Test
    void should_return200AndCustomer_when_getCurrentCustomer() throws Exception {
        when(customerService.getCurrentCustomer("john@doe.com"))
                .thenReturn(new CustomerResponse("cus_1", "john@doe.com", "John"));

        mockMvc.perform(get("/api/v1/payment/customer/get-customer")
                        .header("X-User-Id", "john@doe.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john@doe.com"));
    }
}
