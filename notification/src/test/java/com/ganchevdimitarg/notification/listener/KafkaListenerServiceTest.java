package com.ganchevdimitarg.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KafkaListenerServiceTest {

    @Mock
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_sendEmail_when_messageParses() throws Exception {
        var listener = new KafkaListenerService(emailService, objectMapper);

        listener.listenToMessage("""
                {"recipient":"user@example.com","subject":"Order","msgBody":"You have successfully created an order."}""");

        verify(emailService).sendSimpleMail(new NotificationDto(
                "user@example.com", "Order", "You have successfully created an order."));
    }

    @Test
    void should_throwForRetry_when_messageIsNotJson() {
        var listener = new KafkaListenerService(emailService, objectMapper);

        assertThatThrownBy(() -> listener.listenToMessage("not-json"))
                .isInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(emailService);
    }
}
