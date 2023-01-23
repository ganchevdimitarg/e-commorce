package com.concordeu.mail.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "Mail List")
@Table(name="mail_list" )
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class MailList {
	@Id
	@GeneratedValue(generator = "uuid-string")
	@GenericGenerator(name = "uuid-string",
					  strategy = "org.hibernate.id.UUIDGenerator")
	@Column(name = "id", unique = true, nullable = false, updatable = false)
	private String id;

	private String userId;

	private boolean isUserActive = true;
	private boolean signedForAnnouncements;
	private boolean signedForPromotions;
	private boolean signedForNotifications;
	private Integer sentMailsForUser = 0;




}
