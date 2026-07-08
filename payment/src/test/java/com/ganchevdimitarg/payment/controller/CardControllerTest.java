package com.ganchevdimitarg.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;
import com.ganchevdimitarg.payment.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.payment.service.CardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CardService cardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CardController(cardService))
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    void should_return200AndCard_when_createValid() throws Exception {
        when(cardService.createCard(any(), any(), any())).thenReturn(new CardResponse("card_1", "cus_1"));

        mockMvc.perform(post("/api/v1/payment/card/create-card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john@doe.com")
                        .header("Idempotency-Key", "idem-1")
                        .content(objectMapper.writeValueAsString(new CreateCardCommand("tok_visa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("card_1"));

        verify(cardService).createCard(eq("john@doe.com"), any(), eq("idem-1"));
    }

    @Test
    void should_return400_when_tokenBlank() throws Exception {
        mockMvc.perform(post("/api/v1/payment/card/create-card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john@doe.com")
                        .header("Idempotency-Key", "idem-1")
                        .content(objectMapper.writeValueAsString(new CreateCardCommand(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return200AndCardIds_when_getCards() throws Exception {
        when(cardService.getCards("john@doe.com")).thenReturn(Set.of("card_1", "card_2"));

        mockMvc.perform(get("/api/v1/payment/card/get-cards").header("X-User-Id", "john@doe.com"))
                .andExpect(status().isOk());
    }
}
