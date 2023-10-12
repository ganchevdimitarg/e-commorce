package com.concordeu.auth.service;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.AuthUserDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final ReplyingKafkaTemplate<String, String, String> template;
    private final ObjectMapper objectMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUserDto user = getReplayAuthUserDto(username);
        return new User(user.username(), user.password(), user.grantedAuthorities());
    }

    private AuthUserDto getReplayAuthUserDto(String payload) {
        template.setReplyErrorChecker(record -> {
            Header error = record.headers().lastHeader(Constant.SERVER_SENT_AN_ERROR);
            if (error != null) {
                return new IllegalArgumentException(new String(error.value()));
            } else {
                return null;
            }
        });

        AuthUserDto authUser;

        try {
            authUser = objectMapper.readValue(
                    template.sendAndReceive(new ProducerRecord<>(Constant.GET_USER_BY_USERNAME, payload))
                            .get(5, TimeUnit.SECONDS)
                            .value(),
                    AuthUserDto.class
            );
            System.out.println();
        } catch (JsonProcessingException | TimeoutException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        }

        return authUser;
    }
}
