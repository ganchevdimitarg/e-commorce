package com.concordeu.profile.service;

import com.concordeu.profile.dto.CardDto;
import com.concordeu.profile.dto.PaymentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;

import java.util.concurrent.ExecutionException;

//@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerServiceImpl implements KafkaProducerService {
    public static final String SEND_WELCOME_MAIL = "sentWelcomeMail";
    public static final String SEND_PASSWORD_RESET_TOKEN_MAIL = "sendPasswordResetTokenMail";
    public static final String GET_REQUEST_TO_PAYMENT_SERVICE_GET_CARDS_BY_USERNAME = "requestGetCardsByUsername";
    public static final String POST_REQUEST_TO_PAYMENT_SERVICE_ADD_CARD_TO_CUSTOMER = "postRequestAddCardToCustomer";
    public static final String POST_REQUEST_TO_PAYMENT_SERVICE_CREATE_CUSTOMER = "postRequestCreateCustomer";
    public static final String DELETE_REQUEST_TO_PAYMENT_SERVICE_CUSTOMER_BY_USERNAME= "deleteRequestCustomerByUsername";

//    private final KafkaTemplate<String, NotificationDto> notificationTemplate;
    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;
    @Override
    public String getCard(String username) throws ExecutionException, InterruptedException {
        ProducerRecord<String, String> record = new ProducerRecord<>(GET_REQUEST_TO_PAYMENT_SERVICE_GET_CARDS_BY_USERNAME, null, username, username);
        RequestReplyFuture<String, String, String> future = replyingKafkaTemplate.sendAndReceive(record);
        ConsumerRecord<String, String> response = future.get();
        System.out.println(response);
        return username;
    }

    @Override
    public void sendUserWelcomeMail(String username) {
        /*notificationTemplate.send(
                SEND_WELCOME_MAIL,
                new NotificationDto(
                        username,
                        "Registration",
                        "You have successfully registered. Please log in to your account."
                )
        );
        log.info("Register email sent successfully");*/
    }

    @Override
    public void sendPasswordResetTokenMail(String username, String token) {
        /*notificationTemplate.send(
                SEND_PASSWORD_RESET_TOKEN_MAIL,
                new NotificationDto(
                        username,
                        "Password reset token",
                        String.format("""
                                Please click on the URL below to set a new password.
                                token: %s
                                """, token)
                )
        );
        log.info("Password reset token email sent successfully");*/
    }

    @Override
    public String sendGetRequestGetCardsByUsername(String username) {
       return null;
    }

    @Override
    public String sendPostRequestAddCardToCustomer(CardDto cardDto) {
        return null;
    }

    @Override
    public String sendPostRequestCreateCustomer(PaymentDto paymentDto) {
        return null;
    }

    @Override
    public void sendDeleteRequest(String username) {

    }
}
