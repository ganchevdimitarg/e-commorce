package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.config.KafkaTopics;
import com.ganchevdimitarg.order.dto.NotificationDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void should_sendWithKeyAndCorrelationHeader_when_mailRequested() {
        KafkaTemplate<String, NotificationDto> template = mock(KafkaTemplate.class);
        MailServiceImpl service = new MailServiceImpl(template);

        service.sendUserOrderMail("john");

        ArgumentCaptor<ProducerRecord<String, NotificationDto>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, NotificationDto> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(KafkaTopics.SENT_MAIL);
        assertThat(record.key()).isEqualTo("john");
        assertThat(record.headers().lastHeader("correlationId")).isNotNull();
    }
}
