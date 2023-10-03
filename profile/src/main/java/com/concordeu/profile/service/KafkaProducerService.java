package com.concordeu.profile.service;

import com.concordeu.profile.dto.CardDto;
import com.concordeu.profile.dto.PaymentDto;

import java.util.concurrent.ExecutionException;

public interface KafkaProducerService {
    String getCard(String username) throws ExecutionException, InterruptedException;
    void sendUserWelcomeMail(String notificationDto);
    void sendPasswordResetTokenMail(String username, String token);
    String sendGetRequestGetCardsByUsername(String username);
    String sendPostRequestAddCardToCustomer(CardDto cardDto);
    String sendPostRequestCreateCustomer(PaymentDto paymentDto);
    void sendDeleteRequest(String username);

}
