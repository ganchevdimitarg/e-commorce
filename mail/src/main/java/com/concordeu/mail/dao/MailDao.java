package com.concordeu.mail.dao;

import com.concordeu.mail.domain.Mail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailDao extends JpaRepository<Mail, String> {
}
