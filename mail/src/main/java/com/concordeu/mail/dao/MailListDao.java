package com.concordeu.mail.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.concordeu.mail.domain.MailList;

public interface MailListDao extends JpaRepository<MailList, String> {
	void deleteByUserId(String userId);

	MailList findByUserId(String userId);
}
