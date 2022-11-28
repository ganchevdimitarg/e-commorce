package com.concordeu.order.service;

import com.concordeu.order.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    @Override
    public void sendUserOrderMail(String username) {
        kafkaTemplate.send(
                "orderMail",
                new NotificationDto(
                        username,
                        "Order",
                        "You have successfully created an order."
                )
        );
    }
}
