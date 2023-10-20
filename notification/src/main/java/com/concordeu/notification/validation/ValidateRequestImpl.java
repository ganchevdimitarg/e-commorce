package com.concordeu.notification.validation;

import com.concordeu.client.common.dto.NotificationDto;
import com.concordeu.notification.excaption.InvalidRequestDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(NotificationDto notificationDto) {
        return isValidRecipient(notificationDto.recipient()) &&
                isValidMsgBody(notificationDto.msgBody()) &&
                isValidSubject(notificationDto.subject());

    }

    private boolean isValidSubject(String subject) {
        if (subject.isBlank()) {
            log.warn("Subject code is not correct: {}", subject);
            throw new InvalidRequestDataException(
                    String.format("Subject code is not correct: %s. For example: 9001", subject));
        }
        return true;
    }

    private boolean isValidMsgBody(String text) {
        if (text.isBlank()) {
            log.warn("Text code is not correct: {}", text);
            throw new InvalidRequestDataException(
                    String.format("Text code is not correct: %s. For example: 9001", text));
        }
        return true;
    }


    private boolean isValidRecipient(String recipient) {
        if (recipient.isBlank()) {
            log.warn("Recipient code is not correct: {}", recipient);
            throw new InvalidRequestDataException(
                    String.format("Recipient code is not correct: %s. For example: 9001", recipient));
        }
        return true;
    }
}
