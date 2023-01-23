package com.concordeu.mail.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import com.concordeu.mail.dao.MailListDao;
import com.concordeu.mail.domain.MailList;
import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.enums.EmailClassification;
import com.concordeu.mail.enums.MailTypeEnum;
import com.concordeu.mail.helpers.CreateMailFields;
import com.concordeu.mail.helpers.MailTemplateMaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
	private final JavaMailSender javaMailSender;

	private final MailListDao mailListDao;

	private final MailTemplateMaker mailTemplateMaker;

	private final CreateMailFields createMailFields; // TODO remove only for testing perposees



	@Value("${spring.mail.username}")
	private String sender;


// TODO remove only for testing perposees
	@EventListener(ContextRefreshedEvent.class)
	public void contextRefreshedEvent() {
		sendMailWithHTML(new MailDto("stefan.kehayov.96@gmail.com", "", "" , "1", MailTypeEnum.EMAIL_VERIFICATION,
										 createMailFields.createEmailVerificationFields("https://google.com", "Stefan")));

		sendMailWithHTML(new MailDto("stefan.kehayov.96@gmail.com", "", "" , "1", MailTypeEnum.PASSWORD_RESET,
									 createMailFields.createPasswordResetFields("https://google.com", "Stefan")));
		sendMailWithHTML(new MailDto("stefan.kehayov.96@gmail.com", "", "" , "1", MailTypeEnum.FAILED_PAYMENT,
									 createMailFields.createFailedPayment("https://google.com", "Stefan", "https://google.com/" , "23.68")));
		sendMailWithHTML(new MailDto("stefan.kehayov.96@gmail.com", "", "" , "1", MailTypeEnum.LOGIN_MAIL,
									 createMailFields.createLoginFields("https://google.com", "Stefan", "1.1.1.1" , "Linux", "Brave", "2023-01-15")));

	}



	public String sendSimpleMail(MailDto mailDto) {

		try {
			SimpleMailMessage mailMessage = new SimpleMailMessage();
			mailMessage.setFrom(sender);
			mailMessage.setTo(mailDto.recipient());
			mailMessage.setText(mailDto.msgBody());
			mailMessage.setSubject(mailDto.subject());
			javaMailSender.send(mailMessage);

			log.info(String.format("The email was successfully sent to: %s", mailDto.recipient()));
			return String.format("The email was successfully sent to: %s", mailDto.recipient());

		} catch (MailException e) {
			log.error(e.toString());
			throw new MailSendException(e.toString());
		}
	}

	public String sendMailWithHTML(MailDto mailDto) {

		Optional<MailList> mailListUser = mailListDao.findById(mailDto.userId());

		MailList mailList;

		if (mailListUser.isEmpty()){
			MailList newMailListUser = MailList.builder()
											   .userId(mailDto.userId())
											   .signedForAnnouncements(true)
											   .signedForNotifications(true)
											   .signedForPromotions(true)
												.isUserActive(true)
												.sentMailsForUser(0)
											   .build();
			mailList = newMailListUser;
		}else {
			mailList = mailListUser.get();
		}

		boolean isUserActive = mailList.isUserActive();

		if (!isUserActive){
			return "User is not active";
		}

		EmailClassification emailClassification = mailDto.type().getClassification();
		if(emailClassification == null){
			return "Wrong email classification";
		}

		boolean relevantFlag;
		switch (emailClassification){
		case PROMOTION -> relevantFlag = mailList.isSignedForPromotions();
		case SYSTEM, PAYMENT ->  relevantFlag = true;
		case ANNOUNCEMENT -> relevantFlag = mailList.isSignedForAnnouncements();
		case NOTIFICATION -> relevantFlag = mailList.isSignedForNotifications();
		default -> relevantFlag = false;
		}


		if (!relevantFlag){
			return "This user doesn't have the requirements for this mail send";
		}

		Map<String, String> data;
		try {
			 data = parseToMap(mailDto.fields());
		}catch (Exception e){
			log.error(String.format("Cannot parse the fields. Returned with exception: %s", e));
			return "Cannot parse the fields";
		}


		String contentProcessed;

		try {
			contentProcessed = mailTemplateMaker.getFinalEmailTemplate(mailDto.type().getTemplateFileName() ,data);

		}catch (Exception e){
			log.error(String.format("Not existing template for this email. Returned with exception: %s", e));
			return "Not existing template for this email";
		}


		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		MimeMessageHelper mimeMessageHelper;
		try {
			mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
			mimeMessageHelper.setFrom(sender);
			mimeMessageHelper.setTo(mailDto.recipient());
			mimeMessageHelper.setText(contentProcessed, true);
			mimeMessageHelper.setSubject(StringUtils.substringBetween(contentProcessed, "<title>", "</title>"));

			javaMailSender.send(mimeMessage);
			log.info(String.format("The email was successfully sent to: %s", mailDto.recipient()));

			mailList.setSentMailsForUser(mailList.getSentMailsForUser() + 1);
			mailListDao.save(mailList);
			return String.format("The email was successfully sent to: %s", mailDto.recipient());
		} catch (MessagingException e) {
			log.error(e.toString());
			throw new MailSendException(e.toString());
		}
	}

	private Map<String, String> parseToMap(String value) {
		//"first_name | Jon,last_name | Smith,gender | male"

		String[] keyValuePairs = value.split(",");              //split the string to creat key-value pairs
		Map<String,String> map = new HashMap<>();

		for(String pair : keyValuePairs)                        //iterate over the pairs
		{
			String[] entry = pair.split(";");                   //split the pairs to get key and value
			if(entry[1].trim() == "null" || entry[1].trim() == null){
				break;
			}
			map.put(entry[0].trim(), entry[1].trim());          //add them to the hashmap and trim whitespaces
		}
		return map;
	}

	public String sendMailWithAttachment(MailDto mailDto) {
       /* MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper;
        try {
            mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(sender);
            mimeMessageHelper.setTo(notificationDto.recipient());
            mimeMessageHelper.setText(notificationDto.msgBody());
            mimeMessageHelper.setSubject(notificationDto.subject());

            FileSystemResource file = new FileSystemResource(new File(notificationDto.attachment()));

            mimeMessageHelper.addAttachment(Objects.requireNonNull(file.getFilename()), file);
            javaMailSender.send(mimeMessage);

            log.info(String.format("The email was successfully sent to: %s", notificationDto.recipient()));
            return String.format("The email was successfully sent to: %s", notificationDto.recipient());

        }
        catch (MessagingException e) {
            log.error(e.toString());
            throw new MailSendException(e.toString());
        }*/
		return "";
	}

}

