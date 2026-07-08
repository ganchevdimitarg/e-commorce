package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.config.KafkaTopics;
import com.ganchevdimitarg.order.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    @Override
    public void sendUserOrderMail(String username) {
        NotificationDto payload = new NotificationDto(
                username, "Order", "You have successfully created an order.");
        ProducerRecord<String, NotificationDto> record =
                new ProducerRecord<>(KafkaTopics.ORDER_NOTIFICATION_REQUESTED, username, payload);
        record.headers().add(header("traceId", MDC.get("traceId")));
        record.headers().add(header("correlationId", UUID.randomUUID().toString()));
        kafkaTemplate.send(record);
        log.info("Email notification queued for {}", username);
    }

    private static RecordHeader header(String key, String value) {
        return new RecordHeader(key, (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
}
