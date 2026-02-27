package com.ganchevdimitarg.profile.service;

import reactor.core.publisher.Mono;

public interface MailService {
    Mono<Void> sendUserWelcomeMail(String notificationDto);
    Mono<Void> sendPasswordResetTokenMail(String username, String token);
}
