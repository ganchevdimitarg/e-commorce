package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void should_sendEmailAndPersist_when_requestIsValid() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(GREEN_MAIL.getSmtp().getPort());

        NotificationService notificationService = mock(NotificationService.class);
        NotificationDto dto = new NotificationDto("user@test.com", "Hi", "Your order shipped");
        when(notificationService.createNotification(dto)).thenReturn(dto);

        EmailServiceImpl service = new EmailServiceImpl(sender, notificationService);
        ReflectionTestUtils.setField(service, "sender", "no-reply@test.com");

        NotificationResponse resp = service.sendSimpleMail(dto);

        assertThat(resp.status()).isEqualTo("SENT");
        assertThat(resp.recipient()).isEqualTo("user@test.com");
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }
}
