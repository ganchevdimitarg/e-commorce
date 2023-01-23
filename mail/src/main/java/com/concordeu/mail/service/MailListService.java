package com.concordeu.mail.service;

import com.concordeu.mail.dto.MailListDto;

public interface MailListService {
	String save(MailListDto mailListDto);

	String delete(String userId);

	String update(MailListDto mailListDto);
}
