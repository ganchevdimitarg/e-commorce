package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.event.UserDeletedEvent;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    static final String REGISTERED_TOPIC = "auth.user.registered";
    static final String DELETED_TOPIC    = "auth.user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(REGISTERED_TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for userId {}", event.userId(), ex);
                    } else {
                        log.info("Published UserRegisteredEvent for userId {}", event.userId());
                    }
                });
    }

    public void publishDeleted(UserDeletedEvent event) {
        kafkaTemplate.send(DELETED_TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserDeletedEvent for userId {}", event.userId(), ex);
                    } else {
                        log.info("Published UserDeletedEvent for userId {}", event.userId());
                    }
                });
    }
}
