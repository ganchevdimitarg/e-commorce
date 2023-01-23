package com.concordeu.mail.helpers;

import static com.concordeu.mail.enums.MailFields.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateMailFields {
	@Value("${templates.projectName}")
	private String projectName;
	@Value("${templates.projectURL}")
	private String projectURL;

	@Value("${templates.supportEmail}")
	private String supportEmail;
	@Value("${templates.automaticCardChargeDays}")
	private byte automaticCardChargeDays;

	public String createEmailVerificationFields(String actionURL,String userFirstName ){

		String body =

						PROJECT_NAME.toString().toLowerCase() + ";" + projectName + "," +
						PROJECT_URL.toString().toLowerCase() + ";" + projectURL + "," +
						ACTION_URL.toString().toLowerCase() + ";" + actionURL  + "," +
						USER_FIRST_NAME.toString().toLowerCase() + ";" + userFirstName;

		return body;
	}


	public String createPasswordResetFields(String actionURL,String userFirstName ){

		String body =

						PROJECT_NAME.toString().toLowerCase() + ";" + projectName + "," +
						PROJECT_URL.toString().toLowerCase() + ";" + projectURL + "," +

						ACTION_URL.toString().toLowerCase() + ";" + actionURL  + "," +
						USER_FIRST_NAME.toString().toLowerCase() + ";" + userFirstName;


		return body;
	}


	public String createLoginFields(String actionURL,String userFirstName, String userIP, String userOS, String userBrowser, String loginTime ){

		String body =
						PROJECT_NAME.toString().toLowerCase() + ";" + projectName + "," +
						PROJECT_URL.toString().toLowerCase() + ";" + projectURL + "," +

						ACTION_URL.toString().toLowerCase() + ";" + actionURL  + "," +
						USER_FIRST_NAME.toString().toLowerCase() + ";" + userFirstName  + "," +

						USER_IP.toString().toLowerCase()  + ";" + userIP + "," +
						USER_BROWSER.toString().toLowerCase() + ";" + userBrowser + "," +
						USER_OS.toString().toLowerCase() + ";" + userOS + "," +
						LOGIN_TIME.toString().toLowerCase() + ";" + loginTime;

		return body;
	}
	public String createFailedPayment(String actionURL,String userFirstName,String invoice_url, String value ){

		String body =
						PROJECT_NAME.toString().toLowerCase() + ";" + projectName + "," +
						PROJECT_URL.toString().toLowerCase() + ";" + projectURL + "," +

						ACTION_URL.toString().toLowerCase() + ";" + actionURL  + "," +
						USER_FIRST_NAME.toString().toLowerCase() + ";" + userFirstName  + "," +

						SUPPORT_EMAIL.toString().toLowerCase()  + ";" + supportEmail + "," +
						AUTOMATIC_CARD_CHARGE_DAYS.toString().toLowerCase() + ";" + automaticCardChargeDays + "," +
						INVOICE_URL.toString().toLowerCase() + ";" + invoice_url + "," +
						VALUE.toString().toLowerCase() + ";" + value;

		return body;
	}

}
