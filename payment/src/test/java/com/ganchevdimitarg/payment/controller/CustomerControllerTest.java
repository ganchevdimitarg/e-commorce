package com.ganchevdimitarg.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.dto.CreateCustomerCommand;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.payment.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void should_return200AndCustomer_when_createValid() throws Exception {
        when(customerService.createCustomer(any(), any()))
                .thenReturn(new CustomerResponse("cus_1", "john@doe.com", "John"));

        mockMvc.perform(post("/api/v1/payment/customer/create-customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content(objectMapper.writeValueAsString(new CreateCustomerCommand("john@doe.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cus_1"));

        verify(customerService).createCustomer(any(), eq("idem-1"));
    }

    @Test
    void should_return400_when_usernameNotEmail() throws Exception {
        mockMvc.perform(post("/api/v1/payment/customer/create-customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerCommand("not-an-email"))))
                .andExpect(status().isBadRequest());
    }
}
