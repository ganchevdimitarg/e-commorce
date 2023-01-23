package com.concordeu.mail.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

import com.concordeu.mail.enums.MailTypeEnum;

@Entity(name = "Mail")
@Table(name="mail" )
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Mail {
    @Id
    @GeneratedValue(generator = "uuid-string")
    @GenericGenerator(name = "uuid-string",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String id;
    @Column(name = "recipient", columnDefinition = "TEXT")
    @Email
    private String recipient;
	private String userId;
    @Column(name = "subject", length = 200)
    private String subject;
    @Column(name = "msgBody", columnDefinition = "TEXT")
    @Size(min = 10, max = 150)
    private String msgBody;
    private String attachment;
    @Column(name = "created_on")
    LocalDateTime createdOn;
	private MailTypeEnum type;
	private String fields;



}
