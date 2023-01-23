package com.concordeu.mail.service;

import com.concordeu.mail.dto.MailDto;

public interface EmailService {

    String sendSimpleMail(MailDto mailDto);

    String sendMailWithAttachment(MailDto mailDto);

	String sendMailWithHTML(MailDto mailDto);
}
