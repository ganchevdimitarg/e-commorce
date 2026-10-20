package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PRODUCT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PAYLOAD =
            "{\"eventId\":\"44444444-4444-4444-4444-444444444444\",\"productName\":\"mouse\"}";

    @Mock
    OutboxEventRepository repository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay newRelay() {
        return new OutboxRelay(repository, kafkaTemplate, new OutboxProperties(true, 20, Duration.ofSeconds(5)));
    }

    private static OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(EVENT_ID);
        event.setTopic(ProductTopics.PRODUCT_CREATED);
        event.setMessageKey(PRODUCT_ID);
        event.setPayload(PAYLOAD);
        event.setHeaders(Map.of("eventType", "created", "correlationId", "e1"));
        return event;
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void should_markRowSent_when_sendSucceeds() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        int sent = newRelay().relayOnce();

        assertThat(sent).isEqualTo(1);
        assertThat(event.getSentAt()).isNotNull();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.captor();
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(ProductTopics.PRODUCT_CREATED);
        assertThat(record.key()).isEqualTo(PRODUCT_ID);
        assertThat(record.value()).isEqualTo(PAYLOAD);
        assertThat(header(record, "eventType")).isEqualTo("created");
        assertThat(header(record, "correlationId")).isEqualTo("e1");
    }

    @Test
    void should_returnZero_when_noUnsentRows() {
        when(repository.lockUnsentBatch(20)).thenReturn(List.of());

        assertThat(newRelay().relayOnce()).isZero();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void should_leaveRowUnsent_when_sendFails() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));

        assertThat(newRelay().relayOnce()).isZero();
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void should_stopBatch_when_sendFails() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));

        assertThat(newRelay().relayOnce()).isZero();
        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }
}
