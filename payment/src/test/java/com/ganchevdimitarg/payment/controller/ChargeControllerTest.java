package com.ganchevdimitarg.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;
import com.ganchevdimitarg.payment.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.payment.service.ChargeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChargeControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChargeService chargeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChargeController(chargeService))
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    void should_return200AndCharge_when_createValid() throws Exception {
        when(chargeService.createCharge(any())).thenReturn(new ChargeResponse("ch_1", "succeeded"));

        mockMvc.perform(post("/api/v1/payment/charge/create-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateChargeCommand(
                                "john@doe.com", "cus_1", "card_1", 500L, "usd", "john@doe.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeId").value("ch_1"));
    }

    @Test
    void should_return400_when_amountNotPositive() throws Exception {
        mockMvc.perform(post("/api/v1/payment/charge/create-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateChargeCommand(
                                "john@doe.com", "cus_1", "card_1", 0L, "usd", "john@doe.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return200AndCharge_when_refundValid() throws Exception {
        when(chargeService.refund(any())).thenReturn(new ChargeResponse("ch_1", "succeeded"));

        mockMvc.perform(post("/api/v1/payment/charge/refund-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundChargeCommand("ch_1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeId").value("ch_1"));
    }

    @Test
    void should_return400_when_refundChargeIdBlank() throws Exception {
        mockMvc.perform(post("/api/v1/payment/charge/refund-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundChargeCommand(""))))
                .andExpect(status().isBadRequest());
    }
}
