package com.concordeu.mail.enums;

import javax.persistence.criteria.CriteriaBuilder;

import org.springframework.beans.factory.annotation.Value;

public enum MailFields {
	// common
	PROJECT_URL, PROJECT_NAME, USER_FIRST_NAME, ACTION_URL,
	// failed-payment
	SUPPORT_EMAIL, AUTOMATIC_CARD_CHARGE_DAYS, INVOICE_URL, VALUE,
	//login mail
	USER_IP, USER_BROWSER, USER_OS, LOGIN_TIME;

}
