package com.concordeu.notification.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.Size;

@Entity(name = "Notification")
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
    @Column(name = "recipient", columnDefinition = "TEXT")
    @Size(min = 3, max = 15)
    private String recipient;
    @Column(name = "subject", length = 200)
    private String subject;
    @Column(name = "msgBody", columnDefinition = "TEXT")
    @Size(min = 10, max = 150)
    private String msgBody;
    private String attachment;
}