package com.concordeu.order.service.impl;

import com.concordeu.order.dto.NotificationDto;
import com.concordeu.order.service.MailService;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    @Override
    public void sendUserOrderMail(String username) {
        kafkaTemplate.send(
                "requestMailOrder",
                new NotificationDto(
                        username,
                        "Order",
                        "You have successfully created an order."
                )
        );
        log.info("Email sent successfully");
    }
}