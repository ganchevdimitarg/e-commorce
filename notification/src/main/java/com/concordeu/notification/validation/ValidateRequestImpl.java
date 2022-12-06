package com.concordeu.notification.validation;

import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.excaption.InvalidRequestDataException;
import org.springframework.stereotype.Component;

@Component
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(NotificationDto notificationDto) {
        return isValidRecipient(notificationDto.recipient()) &&
                isValidMsgBody(notificationDto.msgBody()) &&
                isValidSubject(notificationDto.subject());

    }

    private boolean isValidSubject(String subject) {
        if (subject.isBlank()) {
            throw new InvalidRequestDataException(
                    String.format("Subject code is not correct: %s. For example: 9001", subject));
        }
        return true;
    }

    private boolean isValidMsgBody(String text) {
        if (text.isBlank()) {
            throw new InvalidRequestDataException(
                    String.format("Text code is not correct: %s. For example: 9001", text));
        }
        return true;
    }


    private boolean isValidRecipient(String recipient) {
        if (recipient.isBlank()) {
            throw new InvalidRequestDataException(
                    String.format("Recipient code is not correct: %s. For example: 9001", recipient));
        }
        return true;
    }
}
