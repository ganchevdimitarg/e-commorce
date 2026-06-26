package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.event.UserRegisteredEvent;
import com.ganchevdimitarg.profile.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes user-lifecycle events emitted by the auth service.
 *
 * <p>Payloads are plain JSON strings deserialised with the tolerant application
 * {@link ObjectMapper}. A parse failure propagates so the container's
 * {@code DefaultErrorHandler} routes the record to the matching {@code .DLT}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user.registered", groupId = "profile-group")
    public void onUserRegistered(String payload) throws Exception {
        UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
        profileService.createProfileShell(event).block();      // listener thread; block is acceptable
    }
}
