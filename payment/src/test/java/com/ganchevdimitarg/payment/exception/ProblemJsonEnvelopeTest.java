package com.ganchevdimitarg.payment.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.controller.ChargeController;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts {@link ControllerExceptionHandler} renders a {@code BusinessException} as an
 * RFC 9457 {@code application/problem+json} envelope carrying the domain {@code code}
 * and a {@code timestamp}. Standalone MockMvc, no Spring context — fast unit-style test,
 * matching {@code ChargeControllerTest}'s style.
 */
@ExtendWith(MockitoExtension.class)
class ProblemJsonEnvelopeTest {

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
    void should_renderProblemJsonWithCodeAndTimestamp_when_notFoundExceptionThrown() throws Exception {
        when(chargeService.createCharge(any(), any(), any()))
                .thenThrow(new NotFoundException("Customer", "john.doe"));

        mockMvc.perform(post("/api/v1/payment/charge/create-charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john@doe.com")
                        .header("Idempotency-Key", "idem-1")
                        .content(objectMapper.writeValueAsString(new CreateChargeCommand(
                                "order-1", "card_1", 500L, "usd", "john@doe.com"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
