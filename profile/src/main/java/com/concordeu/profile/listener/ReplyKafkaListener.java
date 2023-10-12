package com.concordeu.profile.listener;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.AuthUserDto;
import com.concordeu.profile.repositories.ProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class ReplyKafkaListener {
    private final ProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = Constant.GET_USER_BY_USERNAME,
            groupId = Constant.PROFILE_SERVICE,
            containerFactory = Constant.CONTAINER_FACTORY
    )
    @SendTo
    public AuthUserDto handleCreateCustomer(String username) throws ExecutionException, InterruptedException {
        return profileRepository.findByUsername(username)
                .map(u -> AuthUserDto.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
                        .grantedAuthorities(u.getGrantedAuthorities())
                        .build())
                .toFuture()
                .get();
    }
}
