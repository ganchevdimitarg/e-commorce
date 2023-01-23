package com.concordeu.mail.dto;

public record MailListDto (
	 String userId,
	 boolean signedForAnnouncements,
	 boolean signedForPromotions,
	 boolean signedForNotifications


){}
