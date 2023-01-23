package com.concordeu.mail.dto;

import com.concordeu.mail.enums.MailTypeEnum;

public record MailDto(
		String recipient,
		String subject,
		// used only for simple mail;
		String msgBody,
		String userId,
		MailTypeEnum type,
		String fields
) {
}
