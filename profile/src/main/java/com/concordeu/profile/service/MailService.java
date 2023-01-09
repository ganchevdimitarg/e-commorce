package com.concordeu.profile.service;

public interface MailService {
    void sendUserWelcomeMail(String notificationDto);
    void sendPasswordResetTokenMail(String username, String token);
}
