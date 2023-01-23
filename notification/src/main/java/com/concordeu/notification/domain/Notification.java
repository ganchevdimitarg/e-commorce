package com.concordeu.notification.domain;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "Notification")
@Table(name = "notification")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Notification {
	@Id
	@GeneratedValue(generator = "uuid-string")
	@GenericGenerator(name = "uuid-string",
					  strategy = "org.hibernate.id.UUIDGenerator")
	@Column(name = "id", unique = true, nullable = false, updatable = false)
	private String id;
	@Column(name = "userId")
	private String userId;
	@Column(name = "subject", length = 200)
	private String subject;
	@Column(name = "msgBody", columnDefinition = "TEXT")
	@Size(min = 10, max = 251)
	private String msgBody;
	private String link;
	@Column(name = "created_on")
	private LocalDateTime createdOn;

	private boolean isViewed = false;
}
