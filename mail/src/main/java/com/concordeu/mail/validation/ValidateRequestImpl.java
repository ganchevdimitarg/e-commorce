package com.concordeu.mail.validation;

import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.excaption.InvalidRequestDataException;
import org.springframework.stereotype.Component;

@Component
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(MailDto mailDto) {
        return isValidRecipient(mailDto.recipient()) &&
                isValidMsgBody(mailDto.msgBody()) &&
                isValidSubject(mailDto.subject());

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
