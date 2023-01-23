package com.concordeu.mail.enums;

import static com.concordeu.mail.enums.MailFields.*;


public enum MailTypeEnum {
	EMAIL_VERIFICATION, FAILED_PAYMENT, LOGIN_MAIL, NOTIFICATION, PASSWORD_RESET, USER_INVITATION, TRIAL_EXPORTED, TRIAL_EXPIRING, RECEIPT, PROMOTION, INVOICE;

	public EmailClassification getClassification(){

		EmailClassification emailClassification;
		switch (this){
		case RECEIPT,LOGIN_MAIL,PASSWORD_RESET,USER_INVITATION, EMAIL_VERIFICATION  -> emailClassification = EmailClassification.SYSTEM;
		case INVOICE,FAILED_PAYMENT,TRIAL_EXPIRING,TRIAL_EXPORTED -> emailClassification = EmailClassification.PAYMENT;
		case PROMOTION ->  emailClassification = EmailClassification.PROMOTION;
		case NOTIFICATION -> emailClassification = EmailClassification.NOTIFICATION;

		default -> emailClassification = null;
		}
		return emailClassification;

	}
	public String getTemplateFileName(){
		// The enum is responsible for the naming of the template files
		return  this.toString().toLowerCase() + ".html";

	}

}

