package com.concordeu.profile.listener;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.AuthUserDto;
import com.concordeu.profile.entities.Profile;
import com.concordeu.profile.repositories.ProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class ReplyKafkaListener {
    private final ProfileRepository profileRepository;

    @KafkaListener(
            topics = Constant.GET_USER_BY_USERNAME,
            groupId = Constant.PROFILE_SERVICE,
            containerFactory = Constant.CONTAINER_FACTORY
    )
    @SendTo
    @Observed(
            name = "user.name",
            contextualName = "handleGetUser",
            lowCardinalityKeyValues = {"method", "handleGetUser"}
    )
    public AuthUserDto handleGetUser(String username) {
        Profile profile = profileRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Profile does not exist"));

        return AuthUserDto.builder()
                        .username(profile.getUsername())
                        .password(profile.getPassword())
                        .grantedAuthorities(profile.getGrantedAuthorities())
                        .build();
    }
}
