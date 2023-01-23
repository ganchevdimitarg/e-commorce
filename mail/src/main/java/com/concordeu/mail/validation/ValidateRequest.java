package com.concordeu.mail.validation;

import com.concordeu.mail.dto.MailDto;

public interface ValidateRequest {
    boolean validateRequest(MailDto mailDto);
}
